package com.allset.api.geocoding.provider;

import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import com.allset.api.geocoding.provider.BrasilApiCepProvider.CepLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Encadeia os providers dando a cada um o papel que ele realmente sabe cumprir.
 *
 * <p><b>Quem manda na coordenada é o Nominatim.</b> A BrasilAPI entra antes só
 * para <i>enriquecer</i> a busca com rua e bairro do CEP — informação que o
 * usuário costuma não digitar e que melhora muito o acerto do Nominatim.
 *
 * <p>A ordem já foi a inversa, e o resultado medido foi ruim: a BrasilAPI vencia
 * sempre que trouxesse qualquer coordenada, e o backend {@code open-cep} devolve
 * o centroide do município como preenchimento. Em 20 endereços reais de Fortaleza,
 * 16 voltaram no mesmo ponto, com erro de 2 a 11 km — o suficiente para o raio
 * curto do Express não achar ninguém. Nos mesmos 20 endereços, o Nominatim
 * resolveu todos, 8 deles com precisão de edifício.
 *
 * <p>A coordenada da BrasilAPI sobrou como último recurso, quando o Nominatim não
 * acha nada ou está fora do ar, e sempre marcada como {@link GeocodeConfidence#CITY}:
 * é honestamente o que ela é, e essa marcação já basta para o Express recusar o
 * ponto sem confirmação humana.
 *
 * <p>Falhas operacionais (timeout, 5xx, 429) de um provider não derrubam o lookup —
 * caem no próximo. Só lançamos {@link GeocodingProviderUnavailableException} quando
 * <b>todos</b> os providers da chain falharam.
 */
@Primary
@Component
public class CompositeGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(CompositeGeocodingProvider.class);

    private static final String PROVIDER_NAME = "composite";

    private final BrasilApiCepProvider brasilApi;
    private final NominatimGeocodingProvider nominatim;

    public CompositeGeocodingProvider(BrasilApiCepProvider brasilApi,
                                      NominatimGeocodingProvider nominatim) {
        this.brasilApi = brasilApi;
        this.nominatim = nominatim;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<GeocodeResult> geocode(GeocodeQuery query) {
        boolean hasCep = query.zipCode() != null && !query.zipCode().isBlank();

        if (!hasCep) {
            log.debug("Query sem CEP, indo direto para Nominatim");
            return nominatim.geocode(query);
        }

        Optional<CepLookup> lookup = lookupCep(query.zipCode());

        GeocodeQuery effectiveQuery = lookup
            .map(l -> {
                log.debug("Enriquecendo query com BrasilAPI (service={}, street='{}', district='{}')",
                    l.service(), l.address().street(), l.address().district());
                return enrichQuery(query, l.address());
            })
            .orElse(query);

        try {
            Optional<GeocodeResult> result = nominatim.geocode(effectiveQuery);
            if (result.isPresent()) {
                return result.map(r -> fillGaps(r, lookup.orElse(null)));
            }
            log.info("Nominatim sem resultado para cep={}, tentando coordenada aproximada do CEP",
                query.zipCode());
        } catch (GeocodingProviderUnavailableException | GeocodingRateLimitException ex) {
            log.warn("Nominatim indisponível ({}), tentando coordenada aproximada do CEP",
                ex.getClass().getSimpleName());
            if (lookup.isEmpty()) {
                log.error("Toda a chain de geocoding está indisponível");
                throw ex;
            }
        }

        return lookup
            .filter(CepLookup::hasCoords)
            .map(CompositeGeocodingProvider::toApproximateResult);
    }

    /**
     * Reverse é exclusividade do Nominatim — a BrasilAPI só sabe consultar por CEP.
     */
    @Override
    public Optional<GeocodeResult> reverse(BigDecimal lat, BigDecimal lng) {
        return nominatim.reverse(lat, lng);
    }

    // -------------------------------------------------------------------------

    private Optional<CepLookup> lookupCep(String zipCode) {
        try {
            return brasilApi.lookup(zipCode);
        } catch (GeocodingProviderUnavailableException | GeocodingRateLimitException ex) {
            log.warn("BrasilAPI indisponível ({}), seguindo só com Nominatim",
                ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * A coordenada do CEP é, na melhor das hipóteses, o meio da via — e na pior, o
     * centro do município. Vai marcada como {@code CITY} para que ninguém a trate
     * como ponto de atendimento sem alguém olhar o mapa antes.
     */
    private static GeocodeResult toApproximateResult(CepLookup lookup) {
        return new GeocodeResult(
            lookup.lat(),
            lookup.lng(),
            lookup.displayName(),
            lookup.address(),
            GeocodeConfidence.CITY,
            "brasilapi"
        );
    }

    /**
     * O Nominatim acertou o ponto, mas às vezes deixa rua ou bairro em branco no
     * endereço normalizado. Preenche essas lacunas com o que o CEP já trouxe —
     * sem tocar no que o Nominatim afirmou, e sem tocar na coordenada.
     */
    private static GeocodeResult fillGaps(GeocodeResult result, CepLookup lookup) {
        if (lookup == null || result.normalizedAddress() == null) {
            return result;
        }
        NormalizedAddress n = result.normalizedAddress();
        NormalizedAddress c = lookup.address();

        return new GeocodeResult(
            result.lat(),
            result.lng(),
            result.displayName(),
            new NormalizedAddress(
                firstNonBlank(n.street(),   c.street()),
                n.number(),
                firstNonBlank(n.district(), c.district()),
                firstNonBlank(n.city(),     c.city()),
                firstNonBlank(n.state(),    c.state()),
                firstNonBlank(n.zipCode(),  c.zipCode())
            ),
            result.confidence(),
            result.provider()
        );
    }

    /**
     * Preenche apenas os campos que o usuário deixou vazios — nunca sobrescreve
     * o que ele digitou. Para nossa UX (front manda CEP + cidade), isso adiciona
     * rua e bairro que o usuário não preencheu, sem violar a intenção dele.
     */
    private static GeocodeQuery enrichQuery(GeocodeQuery original, NormalizedAddress fromBrasilApi) {
        return new GeocodeQuery(
            original.zipCode(),
            firstNonBlank(original.street(),   fromBrasilApi.street()),
            original.number(),
            firstNonBlank(original.district(), fromBrasilApi.district()),
            firstNonBlank(original.city(),     fromBrasilApi.city()),
            firstNonBlank(original.state(),    fromBrasilApi.state()),
            original.country()
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }
}
