package com.allset.api.address.service;

import com.allset.api.address.domain.CoordinateSource;
import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.mapper.SavedAddressMapper;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.dto.UserResponse;
import com.allset.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedAddressServiceImplTest {

    private static final BigDecimal LAT = new BigDecimal("-3.731862");
    private static final BigDecimal LNG = new BigDecimal("-38.526669");

    @Mock
    private SavedAddressRepository savedAddressRepository;

    @Mock
    private SavedAddressMapper savedAddressMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private SavedAddressServiceImpl savedAddressService;

    @Test
    void createShouldUnsetPreviousDefaultWhenRequested() {
        UUID userId = UUID.randomUUID();

        stubUserAndSave(userId);

        SavedAddressResponse response = savedAddressService.create(userId, createRequest(
                CoordinateSource.user_pin, null, null, true));

        assertThat(response.isDefault()).isTrue();
        verify(savedAddressRepository).unsetDefaultForUser(userId);
    }

    /**
     * A regressão que esta versão fecha: antes, criar endereço sem coordenada
     * disparava geocoding externo dentro da transação e gravava o que o provider
     * devolvesse. Agora o service só grava o que recebeu.
     */
    @Test
    void createShouldPersistCoordinateExactlyAsReceived() {
        UUID userId = UUID.randomUUID();

        stubUserAndSave(userId);

        savedAddressService.create(userId, createRequest(
                CoordinateSource.device_gps, new BigDecimal("12.50"), null, false));

        SavedAddress saved = captureSaved();
        assertThat(saved.getLat()).isEqualByComparingTo(LAT);
        assertThat(saved.getLng()).isEqualByComparingTo(LNG);
        assertThat(saved.getCoordinateSource()).isEqualTo(CoordinateSource.device_gps);
        assertThat(saved.getCoordinateAccuracyMeters()).isEqualByComparingTo("12.50");
        assertThat(saved.getCoordinateConfirmedAt()).isNotNull();
    }

    @Test
    void createShouldKeepProvenanceEmptyWhenNoCoordinateIsSent() {
        UUID userId = UUID.randomUUID();

        stubUserAndSave(userId);

        savedAddressService.create(userId, new CreateSavedAddressRequest(
                "Casa", "Rua A", "100", null, "Centro", "Fortaleza", "CE", "60000-000",
                null, null, null, null, null, false));

        SavedAddress saved = captureSaved();
        assertThat(saved.getLat()).isNull();
        assertThat(saved.getLng()).isNull();
        assertThat(saved.getCoordinateSource()).isNull();
        assertThat(saved.getCoordinateConfirmedAt()).isNull();
    }

    @Test
    void updateShouldChangeFieldsAndKeepSingleDefault() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);

        stubFindAndSave(userId, addressId, address);

        SavedAddressResponse response = savedAddressService.update(userId, addressId,
                new UpdateSavedAddressRequest(
                        "Trabalho", null, null, null, null, "Caucaia", null, null,
                        null, null, null, null, null, true));

        assertThat(response.label()).isEqualTo("Trabalho");
        assertThat(response.city()).isEqualTo("Caucaia");
        assertThat(response.isDefault()).isTrue();
        verify(savedAddressRepository).unsetDefaultForUser(userId);
    }

    /**
     * Mudar o endereço escrito sem mandar pin novo deixa a coordenada antiga
     * apontando para outro lugar. Ela continua gravada (serve de ponto de partida
     * no mapa), mas perde a procedência e sai do Express até alguém reconfirmar.
     */
    @Test
    void updateShouldDowngradeProvenanceWhenAddressChangesWithoutNewPin() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setCoordinateSource(CoordinateSource.device_gps);
        address.setCoordinateAccuracyMeters(new BigDecimal("8.00"));

        stubFindAndSave(userId, addressId, address);

        SavedAddressResponse response = savedAddressService.update(userId, addressId,
                new UpdateSavedAddressRequest(
                        null, "Rua B", "42", null, null, null, null, null,
                        null, null, null, null, null, null));

        assertThat(address.getCoordinateSource()).isEqualTo(CoordinateSource.legacy);
        assertThat(address.getCoordinateAccuracyMeters()).isNull();
        assertThat(address.getLat()).isEqualByComparingTo(LAT);
        assertThat(response.expressReady()).isFalse();
    }

    @Test
    void districtChangeShouldInvalidateConfirmation() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setCoordinateSource(CoordinateSource.user_pin);
        address.setCoordinateConfirmedAt(java.time.Instant.now());
        stubFindAndSave(userId, addressId, address);

        SavedAddressResponse response = savedAddressService.update(userId, addressId,
                new UpdateSavedAddressRequest(null, null, null, null, "Outro bairro", null,
                        null, null, null, null, null, null, null, null));

        assertThat(response.expressReady()).isFalse();
        assertThat(address.getCoordinateConfirmedAt()).isNull();
    }

    @Test
    void resendingUnchangedAddressShouldPreserveConfirmation() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setCoordinateSource(CoordinateSource.user_pin);
        stubFindAndSave(userId, addressId, address);

        SavedAddressResponse response = savedAddressService.update(userId, addressId,
                new UpdateSavedAddressRequest("Novo apelido", address.getStreet(), address.getNumber(),
                        null, address.getDistrict(), address.getCity(), address.getState(), address.getZipCode(),
                        null, null, null, null, null, null));

        assertThat(response.expressReady()).isTrue();
        assertThat(address.getCoordinateSource()).isEqualTo(CoordinateSource.user_pin);
    }

    @Test
    void updateShouldKeepProvenanceWhenPinIsResentWithTheNewAddress() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setCoordinateSource(CoordinateSource.legacy);

        stubFindAndSave(userId, addressId, address);

        BigDecimal newLat = new BigDecimal("-3.740000");
        BigDecimal newLng = new BigDecimal("-38.500000");

        SavedAddressResponse response = savedAddressService.update(userId, addressId,
                new UpdateSavedAddressRequest(
                        null, "Rua B", "42", null, null, null, null, null,
                        newLat, newLng, CoordinateSource.user_pin, null, null, null));

        assertThat(address.getCoordinateSource()).isEqualTo(CoordinateSource.user_pin);
        assertThat(address.getLat()).isEqualByComparingTo(newLat);
        assertThat(address.getCoordinateConfirmedAt()).isNotNull();
        assertThat(response.expressReady()).isTrue();
    }

    @Test
    void setDefaultShouldPersistAddressAsDefault() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setDefault(false);

        stubFindAndSave(userId, addressId, address);

        SavedAddressResponse response = savedAddressService.setDefault(userId, addressId);

        assertThat(response.isDefault()).isTrue();
        verify(savedAddressRepository).unsetDefaultForUser(userId);
    }

    @Test
    void deleteShouldRemoveOwnedAddress() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);

        when(savedAddressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));

        savedAddressService.delete(userId, addressId);

        verify(savedAddressRepository).delete(address);
    }

    // -------------------------------------------------------------------------

    private void stubUserAndSave(UUID userId) {
        when(userService.findById(userId)).thenReturn(activeUser(userId));
        when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> {
            SavedAddress address = invocation.getArgument(0);
            address.setId(UUID.randomUUID());
            address.setCreatedAt(Instant.now());
            address.setUpdatedAt(Instant.now());
            return address;
        });
        when(savedAddressMapper.toResponse(any(SavedAddress.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    private void stubFindAndSave(UUID userId, UUID addressId, SavedAddress address) {
        when(savedAddressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(savedAddressRepository.save(address)).thenReturn(address);
        when(savedAddressMapper.toResponse(address))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    private SavedAddress captureSaved() {
        ArgumentCaptor<SavedAddress> captor = ArgumentCaptor.forClass(SavedAddress.class);
        verify(savedAddressRepository).save(captor.capture());
        return captor.getValue();
    }

    private CreateSavedAddressRequest createRequest(CoordinateSource source,
                                                    BigDecimal accuracy,
                                                    GeocodeConfidence confidence,
                                                    boolean isDefault) {
        return new CreateSavedAddressRequest(
                "Casa", "Rua A", "100", null, "Centro", "Fortaleza", "CE", "60000-000",
                LAT, LNG, source, accuracy, confidence, isDefault);
    }

    private SavedAddress address(UUID userId, UUID addressId) {
        SavedAddress address = SavedAddress.builder()
                .userId(userId)
                .label("Casa")
                .street("Rua A")
                .number("100")
                .district("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .lat(LAT)
                .lng(LNG)
                .isDefault(true)
                .build();
        address.setId(addressId);
        address.setCreatedAt(Instant.now());
        address.setUpdatedAt(Instant.now());
        return address;
    }

    private SavedAddressResponse toResponse(SavedAddress address) {
        return new SavedAddressResponse(
                address.getId(),
                address.getUserId(),
                address.getLabel(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getDistrict(),
                address.getCity(),
                address.getState(),
                address.getZipCode(),
                address.getLat(),
                address.getLng(),
                address.getCoordinateSource(),
                address.getCoordinateAccuracyMeters(),
                address.getCoordinateConfidence(),
                address.getCoordinateConfirmedAt(),
                com.allset.api.address.domain.CoordinateTrust.isExpressReady(address),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    private UserResponse activeUser(UUID userId) {
        return new UserResponse(
                userId,
                "Usuario",
                "usuario@example.com",
                "+5585999999999",
                java.time.LocalDate.of(1990, 1, 1),
                UserRole.client,
                null,
                true,
                null,
                null,
                0L,
                Instant.now(),
                Instant.now(),
                null
        );
    }
}
