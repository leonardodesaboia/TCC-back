package com.allset.api.geocoding.service;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.dto.ReverseGeocodeRequest;
import com.allset.api.geocoding.exception.AddressNotGeocodableException;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.provider.GeocodingProvider;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeResult;
import com.allset.api.shared.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeocodingServiceImplTest {

    private static final BigDecimal LAT = new BigDecimal("-3.734080");
    private static final BigDecimal LNG = new BigDecimal("-38.494210");

    @Mock
    private GeocodingProvider provider;

    @Mock
    private CacheService cacheService;

    @Mock
    private AppProperties appProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GeocodingServiceImpl service;

    @BeforeEach
    void setUp() {
        when(appProperties.geocodingEnabled()).thenReturn(true);
        when(appProperties.geocodingCacheTtlSeconds()).thenReturn(2_592_000);
        when(appProperties.geocodingNegativeCacheTtlSeconds()).thenReturn(300);
        when(provider.name()).thenReturn("composite");
        service = buildService("-7.9,-41.5,-2.7,-37.2");
    }

    private GeocodingServiceImpl buildService(String boundingBox) {
        when(appProperties.geocodingBoundingBox()).thenReturn(boundingBox);
        return new GeocodingServiceImpl(provider, cacheService, appProperties, objectMapper,
                new GeocodingBounds(appProperties));
    }

    @Test
    void shouldFailFastWhenKillSwitchIsOff() {
        when(appProperties.geocodingEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.geocode(request("Aldeota")))
                .isInstanceOf(GeocodingProviderUnavailableException.class);

        verify(provider, never()).geocode(any());
    }

    @Test
    void shouldReturnCachedResponseWithoutCallingProvider() throws Exception {
        GeocodeResponse cached = new GeocodeResponse(LAT, LNG, "Avenida Dom Luís, 1233",
                normalized(), GeocodeConfidence.ROOFTOP, "nominatim");
        when(cacheService.get(anyString())).thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));

        GeocodeResponse response = service.geocode(request("Aldeota"));

        assertThat(response.lat()).isEqualByComparingTo(LAT);
        verify(provider, never()).geocode(any());
    }

    @Test
    void shouldHonourNegativeCacheWithoutCallingProvider() {
        when(cacheService.get(anyString())).thenReturn(Optional.of("NOT_FOUND"));

        assertThatThrownBy(() -> service.geocode(request("Aldeota")))
                .isInstanceOf(AddressNotGeocodableException.class);

        verify(provider, never()).geocode(any());
    }

    @Test
    void shouldCacheNegativeResultWhenProviderFindsNothing() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.geocode(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.geocode(request("Aldeota")))
                .isInstanceOf(AddressNotGeocodableException.class);

        verify(cacheService).set(anyString(), eq("NOT_FOUND"), eq(300L));
    }

    /**
     * Bairros diferentes são endereços diferentes. A chave antiga não incluía o
     * bairro, então "Aldeota" e "Meireles" colidiam e o segundo recebia a
     * coordenada do primeiro direto do cache.
     */
    @Test
    void cacheKeyShouldDistinguishDistricts() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.geocode(any())).thenReturn(Optional.of(result(LAT, LNG)));

        service.geocode(request("Aldeota"));
        service.geocode(request("Meireles"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, org.mockito.Mockito.times(2)).get(keys.capture());

        assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
    }

    /** "Av. Dom Luís" e "av. dom luis" são a mesma consulta — não devem bater duas vezes. */
    @Test
    void cacheKeyShouldIgnoreCaseAndAccents() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.geocode(any())).thenReturn(Optional.of(result(LAT, LNG)));

        service.geocode(new GeocodeRequest("60160-230", "Avenida Dom Luís", "1233", null,
                "Aldeota", "Fortaleza", "CE"));
        service.geocode(new GeocodeRequest("60160-230", "  avenida dom luis  ", "1233", null,
                "aldeota", "fortaleza", "CE"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, org.mockito.Mockito.times(2)).get(keys.capture());

        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }

    /**
     * Rede de proteção medida na auditoria: o CEP inexistente 99999-999 devolveu
     * 200 com um ponto em Sarandi/PR.
     */
    @Test
    void shouldDiscardResultOutsideTheConfiguredBoundingBox() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.geocode(any())).thenReturn(Optional.of(
                result(new BigDecimal("-23.440000"), new BigDecimal("-51.870000"))));

        assertThatThrownBy(() -> service.geocode(request("Aldeota")))
                .isInstanceOf(AddressNotGeocodableException.class);
    }

    @Test
    void shouldAcceptAnyResultWhenBoundingBoxIsDisabled() {
        service = buildService("");
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.geocode(any())).thenReturn(Optional.of(
                result(new BigDecimal("-23.440000"), new BigDecimal("-51.870000"))));

        assertThat(service.geocode(request("Aldeota")).lat())
                .isEqualByComparingTo("-23.440000");
    }

    /**
     * Cache é otimização, não dependência: com o Redis fora do ar o lookup segue
     * pelo provider. Antes desta versão a chamada ficava 60s pendurada e dava 500.
     */
    @Test
    void shouldDegradeGracefullyWhenCacheIsDown() {
        when(cacheService.get(anyString())).thenThrow(new QueryTimeoutException("redis down"));
        org.mockito.Mockito.doThrow(new QueryTimeoutException("redis down"))
                .when(cacheService).set(anyString(), anyString(), anyLong());
        when(provider.geocode(any())).thenReturn(Optional.of(result(LAT, LNG)));

        GeocodeResponse response = service.geocode(request("Aldeota"));

        assertThat(response.lat()).isEqualByComparingTo(LAT);
    }

    @Test
    void reverseShouldKeepTheCoordinateSentByTheUser() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        // Provider devolve o ponto do prédio mapeado, alguns metros ao lado.
        when(provider.reverse(any(), any())).thenReturn(Optional.of(
                result(new BigDecimal("-3.734900"), new BigDecimal("-38.495000"))));

        GeocodeResponse response = service.reverse(new ReverseGeocodeRequest(LAT, LNG));

        assertThat(response.lat()).isEqualByComparingTo(LAT);
        assertThat(response.lng()).isEqualByComparingTo(LNG);
        assertThat(response.normalizedAddress().street()).isEqualTo("Avenida Dom Luís");
    }

    @Test
    void reverseCacheHitShouldPreserveTheExactNewPin() throws Exception {
        GeocodeResponse cached = new GeocodeResponse(LAT, LNG, "Avenida Dom Luís, 1233",
                normalized(), GeocodeConfidence.ROOFTOP, "nominatim");
        when(cacheService.get(anyString())).thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));
        BigDecimal newLat = new BigDecimal("-3.7340801");
        BigDecimal newLng = new BigDecimal("-38.4942099");

        GeocodeResponse response = service.reverse(new ReverseGeocodeRequest(newLat, newLng));

        assertThat(response.lat()).isEqualByComparingTo(newLat);
        assertThat(response.lng()).isEqualByComparingTo(newLng);
        assertThat(response.normalizedAddress()).isEqualTo(cached.normalizedAddress());
        verify(provider, never()).reverse(any(), any());
    }

    /** Arrastar o pin um pixel não pode virar chave nova, senão o cache não serve. */
    @Test
    void reverseCacheKeyShouldRoundToAboutOneMeter() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.reverse(any(), any())).thenReturn(Optional.of(result(LAT, LNG)));

        service.reverse(new ReverseGeocodeRequest(new BigDecimal("-3.7340801"), new BigDecimal("-38.4942101")));
        service.reverse(new ReverseGeocodeRequest(new BigDecimal("-3.7340799"), new BigDecimal("-38.4942099")));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, org.mockito.Mockito.times(2)).get(keys.capture());

        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void reverseShouldFailWhenProviderRecognisesNothing() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(provider.reverse(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reverse(new ReverseGeocodeRequest(LAT, LNG)))
                .isInstanceOf(AddressNotGeocodableException.class);
    }

    // -------------------------------------------------------------------------

    private static GeocodeRequest request(String district) {
        return new GeocodeRequest("60160-230", "Avenida Dom Luís", "1233", null,
                district, "Fortaleza", "CE");
    }

    private static GeocodeResult result(BigDecimal lat, BigDecimal lng) {
        return new GeocodeResult(lat, lng, "Avenida Dom Luís, 1233",
                normalized(), GeocodeConfidence.ROOFTOP, "nominatim");
    }

    private static NormalizedAddress normalized() {
        return new NormalizedAddress("Avenida Dom Luís", "1233", "Aldeota", "Fortaleza", "CE", "60160-230");
    }
}
