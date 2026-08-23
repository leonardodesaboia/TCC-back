package com.allset.api.geocoding.provider;

import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.provider.BrasilApiCepProvider.CepLookup;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeQuery;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trava a ordem dos providers.
 *
 * <p>A ordem inversa já esteve em produção e produziu o defeito central da
 * auditoria: a BrasilAPI vencia sempre que trouxesse qualquer coordenada, e o
 * backend {@code open-cep} devolve o centroide do município. Se alguém reinverter,
 * estes testes quebram.
 */
@ExtendWith(MockitoExtension.class)
class CompositeGeocodingProviderTest {

    private static final String CEP = "60160-230";

    /** O ponto que a BrasilAPI devolveu para 16 dos 20 endereços testados. */
    private static final BigDecimal CENTROID_LAT = new BigDecimal("-3.717220");
    private static final BigDecimal CENTROID_LNG = new BigDecimal("-38.543060");

    private static final BigDecimal REAL_LAT = new BigDecimal("-3.734080");
    private static final BigDecimal REAL_LNG = new BigDecimal("-38.494210");

    @Mock
    private BrasilApiCepProvider brasilApi;

    @Mock
    private NominatimGeocodingProvider nominatim;

    @InjectMocks
    private CompositeGeocodingProvider provider;

    @Test
    void shouldPreferNominatimCoordinateOverBrasilApiCentroid() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(CENTROID_LAT, CENTROID_LNG)));
        when(nominatim.geocode(any())).thenReturn(Optional.of(nominatimResult()));

        Optional<GeocodeResult> result = provider.geocode(query());

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualByComparingTo(REAL_LAT);
        assertThat(result.get().lng()).isEqualByComparingTo(REAL_LNG);
        assertThat(result.get().provider()).isEqualTo("nominatim");
        verify(brasilApi, never()).geocode(any());
    }

    @Test
    void shouldEnrichNominatimQueryWithStreetAndDistrictFromCep() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(null, null)));
        when(nominatim.geocode(any())).thenReturn(Optional.of(nominatimResult()));

        // Usuário digitou só CEP, número e cidade — rua e bairro vêm do CEP.
        provider.geocode(new GeocodeQuery(CEP, null, "1233", null, "Fortaleza", "CE", "BR"));

        ArgumentCaptor<GeocodeQuery> captor = ArgumentCaptor.forClass(GeocodeQuery.class);
        verify(nominatim).geocode(captor.capture());

        assertThat(captor.getValue().street()).isEqualTo("Avenida Dom Luís");
        assertThat(captor.getValue().district()).isEqualTo("Aldeota");
        assertThat(captor.getValue().number()).isEqualTo("1233");
    }

    @Test
    void shouldNotOverwriteWhatTheUserTyped() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(null, null)));
        when(nominatim.geocode(any())).thenReturn(Optional.of(nominatimResult()));

        provider.geocode(new GeocodeQuery(CEP, "Rua do Usuario", "10", "Bairro do Usuario",
                "Fortaleza", "CE", "BR"));

        ArgumentCaptor<GeocodeQuery> captor = ArgumentCaptor.forClass(GeocodeQuery.class);
        verify(nominatim).geocode(captor.capture());

        assertThat(captor.getValue().street()).isEqualTo("Rua do Usuario");
        assertThat(captor.getValue().district()).isEqualTo("Bairro do Usuario");
    }

    /**
     * Sem resultado no Nominatim, a coordenada do CEP entra como último recurso —
     * mas marcada CITY, que é o que ela realmente é. Essa marcação é o que impede
     * o Express de aceitar o ponto sem confirmação humana.
     */
    @Test
    void shouldFallBackToCepCoordinateMarkedAsApproximate() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(CENTROID_LAT, CENTROID_LNG)));
        when(nominatim.geocode(any())).thenReturn(Optional.empty());

        Optional<GeocodeResult> result = provider.geocode(query());

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(GeocodeConfidence.CITY);
        assertThat(result.get().provider()).isEqualTo("brasilapi");
    }

    @Test
    void shouldReturnEmptyWhenNeitherProviderHasCoordinates() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(null, null)));
        when(nominatim.geocode(any())).thenReturn(Optional.empty());

        assertThat(provider.geocode(query())).isEmpty();
    }

    @Test
    void shouldFallBackToCepCoordinateWhenNominatimIsDown() {
        when(brasilApi.lookup(CEP)).thenReturn(Optional.of(cepLookup(CENTROID_LAT, CENTROID_LNG)));
        when(nominatim.geocode(any())).thenThrow(new GeocodingProviderUnavailableException());

        Optional<GeocodeResult> result = provider.geocode(query());

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(GeocodeConfidence.CITY);
    }

    @Test
    void shouldPropagateFailureWhenTheWholeChainIsDown() {
        when(brasilApi.lookup(CEP)).thenThrow(new GeocodingProviderUnavailableException());
        when(nominatim.geocode(any())).thenThrow(new GeocodingProviderUnavailableException());

        assertThatThrownBy(() -> provider.geocode(query()))
                .isInstanceOf(GeocodingProviderUnavailableException.class);
    }

    @Test
    void shouldSkipCepLookupWhenQueryHasNoCep() {
        when(nominatim.geocode(any())).thenReturn(Optional.of(nominatimResult()));

        provider.geocode(new GeocodeQuery(null, "Avenida Dom Luís", "1233", null,
                "Fortaleza", "CE", "BR"));

        verify(brasilApi, never()).lookup(any());
    }

    @Test
    void reverseShouldDelegateToNominatim() {
        when(nominatim.reverse(REAL_LAT, REAL_LNG)).thenReturn(Optional.of(nominatimResult()));

        assertThat(provider.reverse(REAL_LAT, REAL_LNG)).isPresent();
    }

    // -------------------------------------------------------------------------

    private static GeocodeQuery query() {
        return new GeocodeQuery(CEP, "Avenida Dom Luís", "1233", "Aldeota", "Fortaleza", "CE", "BR");
    }

    private static CepLookup cepLookup(BigDecimal lat, BigDecimal lng) {
        return new CepLookup(
                new NormalizedAddress("Avenida Dom Luís", null, "Aldeota", "Fortaleza", "CE", CEP),
                "Avenida Dom Luís, Aldeota, Fortaleza, CE",
                lat,
                lng,
                "open-cep"
        );
    }

    private static GeocodeResult nominatimResult() {
        return new GeocodeResult(
                REAL_LAT,
                REAL_LNG,
                "Edifício Harmony Medical Center, 1233, Avenida Dom Luís",
                new NormalizedAddress("Avenida Dom Luís", "1233", null, "Fortaleza", "CE", CEP),
                GeocodeConfidence.ROOFTOP,
                "nominatim"
        );
    }
}
