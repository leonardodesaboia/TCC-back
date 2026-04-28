package com.allset.api.address.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.mapper.SavedAddressMapper;
import com.allset.api.address.repository.SavedAddressRepository;
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
        CreateSavedAddressRequest request = new CreateSavedAddressRequest(
                "Casa",
                "Rua A",
                "100",
                null,
                "Centro",
                "Fortaleza",
                "CE",
                "60000-000",
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                true
        );

        when(userService.findById(userId)).thenReturn(activeUser(userId));
        when(savedAddressRepository.save(any(SavedAddress.class))).thenAnswer(invocation -> {
            SavedAddress address = invocation.getArgument(0);
            address.setId(UUID.randomUUID());
            address.setCreatedAt(Instant.now());
            address.setUpdatedAt(Instant.now());
            return address;
        });
        when(savedAddressMapper.toResponse(any(SavedAddress.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        SavedAddressResponse response = savedAddressService.create(userId, request);

        assertThat(response.isDefault()).isTrue();
        verify(savedAddressRepository).unsetDefaultForUser(userId);
    }

    @Test
    void updateShouldChangeFieldsAndKeepSingleDefault() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);

        when(savedAddressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(savedAddressRepository.save(address)).thenReturn(address);
        when(savedAddressMapper.toResponse(address)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        SavedAddressResponse response = savedAddressService.update(userId, addressId, new UpdateSavedAddressRequest(
                "Trabalho",
                null,
                null,
                null,
                null,
                "Caucaia",
                null,
                null,
                null,
                null,
                true
        ));

        assertThat(response.label()).isEqualTo("Trabalho");
        assertThat(response.city()).isEqualTo("Caucaia");
        assertThat(response.isDefault()).isTrue();
        verify(savedAddressRepository).unsetDefaultForUser(userId);
    }

    @Test
    void setDefaultShouldPersistAddressAsDefault() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        SavedAddress address = address(userId, addressId);
        address.setDefault(false);

        when(savedAddressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(address));
        when(savedAddressRepository.save(address)).thenReturn(address);
        when(savedAddressMapper.toResponse(address)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

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
                .lat(new BigDecimal("-3.731862"))
                .lng(new BigDecimal("-38.526669"))
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
