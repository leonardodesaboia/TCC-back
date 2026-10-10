package com.allset.api.geocoding.provider;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeQuery;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Testa o provider contra um Nominatim falso local, para poder olhar a requisição
 * que realmente sai — não a que a gente acha que está saindo.
 *
 * <p>Existe por causa de um defeito silencioso: a URI era montada já codificada e
 * entregue ao {@code RestClient} como <i>String</i>, que a tratava como template e
 * codificava de novo. O espaço virava {@code %2520}, o Nominatim respondia 200 com
 * lista vazia, e a aplicação concluía "endereço não localizado" para <b>todo</b>
 * endereço com espaço no nome. Nenhum teste unitário de montagem de URI pegaria
 * isso, porque a URI montada estava certa — quem estragava era a camada seguinte.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NominatimGeocodingProviderTest {

    private static final String BODY = """
        [{"place_id":1,"lat":"-3.7351836","lon":"-38.4897255","display_name":"Edifício Harmony, 1233, Avenida Dom Luís",
          "addresstype":"building","address":{"road":"Avenida Dom Luís","house_number":"1233","suburb":"Meireles",
          "city":"Fortaleza","ISO3166-2-lvl4":"BR-CE","postcode":"60160-230"}}]
        """;

    private static final String REVERSE_BODY = """
        {"place_id":2,"lat":"-3.7340800","lon":"-38.4942100","display_name":"Azul Tecnologia, 807, Avenida Dom Luís",
         "addresstype":"office","address":{"road":"Avenida Dom Luís","house_number":"807","suburb":"Meireles",
         "city":"Fortaleza","ISO3166-2-lvl4":"BR-CE","postcode":"60160-230"}}
        """;

    @Mock
    private AppProperties appProperties;

    private HttpServer server;
    private final List<String> receivedQueries = new ArrayList<>();
    /** Uma resposta por requisição, na ordem. A última se repete se acabarem. */
    private final Deque<String> responses = new ArrayDeque<>();
    private String responseBody = BODY;

    private NominatimGeocodingProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        receivedQueries.clear();
        responses.clear();
        responseBody = BODY;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedQueries.add(exchange.getRequestURI().getRawQuery());
            String body = responses.isEmpty() ? responseBody : responses.poll();
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();

        when(appProperties.geocodingBaseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(appProperties.geocodingUserAgent()).thenReturn("AllSet-Test/1.0 (test@allset.com.br)");
        when(appProperties.geocodingMinIntervalMs()).thenReturn(0);
        when(appProperties.geocodingMaxWaitMs()).thenReturn(1_000);

        provider = new NominatimGeocodingProvider(appProperties, new NominatimRateLimiter(appProperties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSendQueryParametersEncodedExactlyOnce() {
        provider.geocode(query());

        assertThat(receivedQueries).hasSize(1);
        String raw = receivedQueries.get(0);

        // O sintoma do defeito: espaço vira %2520 em vez de %20.
        assertThat(raw).doesNotContain("%2520");

        String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
        assertThat(decoded).contains("street=1233 Avenida Dom Luis");
        assertThat(decoded).contains("city=Fortaleza");
        assertThat(decoded).contains("postalcode=60160230");
    }

    @Test
    void shouldStopAtTheFirstAttemptThatReturnsSomething() {
        Optional<GeocodeResult> result = provider.geocode(query());

        assertThat(receivedQueries).hasSize(1);
        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo("-3.735184");
        assertThat(result.get().confidence()).isEqualTo(GeocodeConfidence.ROOFTOP);
        assertThat(result.get().normalizedAddress().street()).isEqualTo("Avenida Dom Luís");
        assertThat(result.get().normalizedAddress().state()).isEqualTo("CE");
    }

    /** Estruturada vazia → busca livre → street-level. Três requisições, nesta ordem. */
    @Test
    void shouldWalkTheThreeAttemptCascadeWhenNothingMatches() {
        responseBody = "[]";

        Optional<GeocodeResult> result = provider.geocode(query());

        assertThat(result).isEmpty();
        assertThat(receivedQueries).hasSize(3);
        assertThat(receivedQueries.get(0)).contains("street=");
        assertThat(receivedQueries.get(1)).contains("q=");
        // A última tentativa larga número e CEP, que é o que trava prédio não mapeado.
        String lastDecoded = URLDecoder.decode(receivedQueries.get(2), StandardCharsets.UTF_8);
        assertThat(lastDecoded).doesNotContain("1233");
        assertThat(lastDecoded).doesNotContain("postalcode");
    }

    @Test
    void reverseShouldQueryTheReverseEndpointWithTheGivenPoint() {
        responseBody = REVERSE_BODY;

        Optional<GeocodeResult> result = provider.reverse(
                new BigDecimal("-3.734080"), new BigDecimal("-38.494210"));

        assertThat(result).isPresent();
        assertThat(receivedQueries).hasSize(1);
        assertThat(receivedQueries.get(0)).contains("lat=-3.734080");
        assertThat(receivedQueries.get(0)).contains("lon=-38.494210");
        assertThat(result.get().normalizedAddress().number()).isEqualTo("807");
    }

    // ---------------------------------------------------------------- bairro

    /**
     * A busca estruturada do Nominatim não aceita bairro — só street, city, state e
     * postalcode. Numa avenida que atravessa a cidade, o ponto cai em qualquer trecho
     * dela. Medido em 20 endereços de Fortaleza: 8 no bairro errado, um a 3,6 km.
     *
     * <p>Importa mais do que parece porque resultado impreciso obriga a pessoa a tocar
     * no mapa, e o toque promove o ponto a {@code user_pin}, que o Express aceita sem
     * discutir. Abrir o mapa no trecho errado é o que lava uma sugestão ruim.
     */
    @Test
    void shouldPreferFreeFormWhenItLandsInTheRequestedDistrict() {
        queue(roadIn("Meireles", "-3.7250060", "-38.5023870"),
              roadIn("Aldeota", "-3.7487360", "-38.4970080"));

        Optional<GeocodeResult> result = provider.geocode(queryInDistrict("Aldeota"));

        assertThat(receivedQueries).hasSize(2);
        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo("-3.748736");
        assertThat(result.get().normalizedAddress().district()).isEqualTo("Aldeota");
    }

    @Test
    void shouldKeepStructuredResultWhenFreeFormMissesTheDistrictToo() {
        queue(roadIn("Meireles", "-3.7250060", "-38.5023870"),
              roadIn("Centro", "-3.7273550", "-38.5281140"));

        Optional<GeocodeResult> result = provider.geocode(queryInDistrict("Aldeota"));

        assertThat(receivedQueries).hasSize(2);
        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo("-3.725006");
    }

    @Test
    void shouldKeepStructuredResultWhenFreeFormFindsNothing() {
        queue(roadIn("Meireles", "-3.7250060", "-38.5023870"), "[]");

        Optional<GeocodeResult> result = provider.geocode(queryInDistrict("Aldeota"));

        assertThat(receivedQueries).hasSize(2);
        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo("-3.725006");
    }

    /** Precisão de edifício já está no lugar certo — não gasta requisição extra. */
    @Test
    void shouldNotRefineRooftopResults() {
        provider.geocode(queryInDistrict("Aldeota"));

        assertThat(receivedQueries).hasSize(1);
    }

    @Test
    void shouldNotRefineWhenNoDistrictWasInformed() {
        queue(roadIn("Meireles", "-3.7250060", "-38.5023870"));

        Optional<GeocodeResult> result = provider.geocode(
                new GeocodeQuery("60160-230", "Avenida Dom Luís", "1233", null,
                        "Fortaleza", "CE", "BR"));

        assertThat(receivedQueries).hasSize(1);
        assertThat(result).isPresent();
    }

    /** Acento e caixa não podem virar bairro diferente. */
    @Test
    void districtComparisonShouldIgnoreCaseAndAccents() {
        queue(roadIn("Meireles", "-3.7250060", "-38.5023870"),
              roadIn("Dionísio Torres", "-3.7527730", "-38.5120680"));

        Optional<GeocodeResult> result = provider.geocode(queryInDistrict("dionisio torres"));

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo("-3.752773");
    }

    // -------------------------------------------------------------------------

    private void queue(String... bodies) {
        for (String body : bodies) {
            responses.add(body);
        }
    }

    private static String roadIn(String district, String lat, String lon) {
        return ("[{\"place_id\":9,\"lat\":\"%s\",\"lon\":\"%s\","
                + "\"display_name\":\"Avenida Dom Luís, %s, Fortaleza\","
                + "\"addresstype\":\"road\",\"address\":{\"road\":\"Avenida Dom Luís\","
                + "\"suburb\":\"%s\",\"city\":\"Fortaleza\",\"ISO3166-2-lvl4\":\"BR-CE\","
                + "\"postcode\":\"60160-230\"}}]").formatted(lat, lon, district, district);
    }

    private static GeocodeQuery queryInDistrict(String district) {
        return new GeocodeQuery("60160-230", "Avenida Dom Luís", "1233", district,
                "Fortaleza", "CE", "BR");
    }

    private static GeocodeQuery query() {
        return new GeocodeQuery("60160-230", "Avenida Dom Luís", "1233", "Meireles",
                "Fortaleza", "CE", "BR");
    }
}
