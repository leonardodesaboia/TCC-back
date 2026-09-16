package com.allset.api.address.domain;

import com.allset.api.geocoding.dto.GeocodeConfidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a tabela de decisão que separa "tem coordenada" de "tem coordenada boa".
 * Foi a confusão entre as duas coisas que deixava o Express abrir busca de 300 m
 * em cima do centro de Fortaleza.
 */
class CoordinateTrustTest {

    @Test
    void deviceGpsIsAccepted() {
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.device_gps, null)).isTrue();
    }

    @Test
    void userPinIsAccepted() {
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.user_pin, null)).isTrue();
    }

    @Test
    void geocodedIsAcceptedOnlyAtRooftopPrecision() {
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.geocoded, GeocodeConfidence.ROOFTOP)).isTrue();
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.geocoded, GeocodeConfidence.INTERPOLATED)).isFalse();
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.geocoded, GeocodeConfidence.CITY)).isFalse();
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.geocoded, null)).isFalse();
    }

    @Test
    void legacyIsRejected() {
        assertThat(CoordinateTrust.isExpressReady(CoordinateSource.legacy, GeocodeConfidence.ROOFTOP)).isFalse();
    }

    @Test
    void missingSourceIsRejected() {
        assertThat(CoordinateTrust.isExpressReady((CoordinateSource) null, GeocodeConfidence.ROOFTOP)).isFalse();
    }

    /** Nenhuma procedência salva um endereço que não tem ponto nenhum. */
    @ParameterizedTest
    @EnumSource(CoordinateSource.class)
    void addressWithoutCoordinatesIsAlwaysRejected(CoordinateSource source) {
        SavedAddress address = SavedAddress.builder()
                .street("Rua A")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .coordinateSource(source)
                .build();

        assertThat(CoordinateTrust.isExpressReady(address)).isFalse();
    }

    @Test
    void addressWithCoordinatesFollowsTheSourceRule() {
        SavedAddress address = SavedAddress.builder()
                .street("Rua A")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .lat(new BigDecimal("-3.731862"))
                .lng(new BigDecimal("-38.526669"))
                .coordinateSource(CoordinateSource.user_pin)
                .build();

        assertThat(CoordinateTrust.isExpressReady(address)).isTrue();

        address.setCoordinateSource(CoordinateSource.legacy);
        assertThat(CoordinateTrust.isExpressReady(address)).isFalse();
    }
}
