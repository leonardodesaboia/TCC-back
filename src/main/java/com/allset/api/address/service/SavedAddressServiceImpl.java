package com.allset.api.address.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.exception.SavedAddressNotFoundException;
import com.allset.api.address.mapper.SavedAddressMapper;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SavedAddressServiceImpl implements SavedAddressService {

    private final SavedAddressRepository savedAddressRepository;
    private final SavedAddressMapper savedAddressMapper;
    private final UserService userService;

    @Override
    public SavedAddressResponse create(UUID userId, CreateSavedAddressRequest request) {
        // Valida que o usuário existe — lança UserNotFoundException (404) se não existir
        userService.findById(userId);

        if (request.isDefault()) {
            savedAddressRepository.unsetDefaultForUser(userId);
        }

        SavedAddress address = SavedAddress.builder()
            .userId(userId)
            .label(request.label())
            .street(request.street())
            .number(request.number())
            .complement(request.complement())
            .district(request.district())
            .city(request.city())
            .state(request.state())
            .zipCode(request.zipCode())
            .lat(request.lat())
            .lng(request.lng())
            .isDefault(request.isDefault())
            .build();

        return savedAddressMapper.toResponse(savedAddressRepository.save(address));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedAddressResponse> findAllByUser(UUID userId) {
        // Garante que o usuário existe antes de retornar lista vazia
        userService.findById(userId);
        return savedAddressMapper.toResponseList(savedAddressRepository.findAllByUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public SavedAddressResponse findByIdAndUser(UUID userId, UUID id) {
        return savedAddressMapper.toResponse(findOwnedAddress(userId, id));
    }

    @Override
    public SavedAddressResponse update(UUID userId, UUID id, UpdateSavedAddressRequest request) {
        SavedAddress address = findOwnedAddress(userId, id);

        if (request.label() != null)      { address.setLabel(request.label()); }
        if (request.street() != null)     { address.setStreet(request.street()); }
        if (request.number() != null)     { address.setNumber(request.number()); }
        if (request.complement() != null) { address.setComplement(request.complement()); }
        if (request.district() != null)   { address.setDistrict(request.district()); }
        if (request.city() != null)       { address.setCity(request.city()); }
        if (request.state() != null)      { address.setState(request.state()); }
        if (request.zipCode() != null)    { address.setZipCode(request.zipCode()); }
        if (request.lat() != null)        { address.setLat(request.lat()); }
        if (request.lng() != null)        { address.setLng(request.lng()); }

        if (request.isDefault() != null) {
            if (Boolean.TRUE.equals(request.isDefault())) {
                savedAddressRepository.unsetDefaultForUser(userId);
            }
            address.setDefault(request.isDefault());
        }

        return savedAddressMapper.toResponse(savedAddressRepository.save(address));
    }

    @Override
    public void delete(UUID userId, UUID id) {
        savedAddressRepository.delete(findOwnedAddress(userId, id));
    }

    @Override
    public SavedAddressResponse setDefault(UUID userId, UUID id) {
        SavedAddress address = findOwnedAddress(userId, id);
        savedAddressRepository.unsetDefaultForUser(userId);
        address.setDefault(true);
        return savedAddressMapper.toResponse(savedAddressRepository.save(address));
    }

    // -------------------------------------------------------------------------

    /**
     * Busca o endereço verificando ownership em um único round-trip.
     * O mesmo 404 é lançado tanto para "não existe" quanto para "pertence a outro usuário",
     * evitando information leakage sobre a existência do recurso.
     */
    private SavedAddress findOwnedAddress(UUID userId, UUID id) {
        return savedAddressRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new SavedAddressNotFoundException(id));
    }
}
