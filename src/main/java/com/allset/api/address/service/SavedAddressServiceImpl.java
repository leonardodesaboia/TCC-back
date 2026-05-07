package com.allset.api.address.service;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.exception.SavedAddressNotFoundException;
import com.allset.api.address.mapper.SavedAddressMapper;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import com.allset.api.geocoding.service.GeocodingService;
import com.allset.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SavedAddressServiceImpl implements SavedAddressService {

    private static final Logger log = LoggerFactory.getLogger(SavedAddressServiceImpl.class);

    private final SavedAddressRepository savedAddressRepository;
    private final SavedAddressMapper savedAddressMapper;
    private final UserService userService;
    private final GeocodingService geocodingService;

    @Override
    public SavedAddressResponse create(UUID userId, CreateSavedAddressRequest request) {
        // Valida que o usuário existe — lança UserNotFoundException (404) se não existir
        userService.findById(userId);

        if (request.isDefault()) {
            savedAddressRepository.unsetDefaultForUser(userId);
        }

        BigDecimal lat = request.lat();
        BigDecimal lng = request.lng();
        if (lat == null || lng == null) {
            GeocodeResponse geocoded = tryGeocode(toGeocodeRequest(request));
            if (geocoded != null) {
                lat = geocoded.lat();
                lng = geocoded.lng();
            }
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
            .lat(lat)
            .lng(lng)
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

        boolean addressFieldsChanged =
            request.street()  != null
            || request.number()  != null
            || request.zipCode() != null
            || request.city()    != null
            || request.state()   != null;

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

        // Re-geocodificar apenas quando algum campo de endereço mudou e o request
        // não trouxe lat/lng explícitos (front que confirmou o pin não é re-geocodificado).
        if (addressFieldsChanged && request.lat() == null && request.lng() == null) {
            GeocodeResponse geocoded = tryGeocode(new GeocodeRequest(
                address.getZipCode(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getDistrict(),
                address.getCity(),
                address.getState()
            ));
            if (geocoded != null) {
                address.setLat(geocoded.lat());
                address.setLng(geocoded.lng());
            }
        }

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

    /**
     * Geocodifica o endereço tolerando falha do provider:
     * <ul>
     *   <li>endereço não localizável → propaga {@code AddressNotGeocodableException} (422);</li>
     *   <li>provider offline / rate limit → log warn e devolve {@code null}, deixando lat/lng nulos
     *       no banco (o front pode reexecutar lookup e atualizar via PUT depois).</li>
     * </ul>
     */
    private GeocodeResponse tryGeocode(GeocodeRequest request) {
        try {
            return geocodingService.geocode(request);
        } catch (GeocodingProviderUnavailableException ex) {
            log.warn("Geocoding indisponível, salvando endereço sem coordenadas: {}", ex.getMessage());
            return null;
        } catch (GeocodingRateLimitException ex) {
            log.warn("Geocoding sob rate limit, salvando endereço sem coordenadas: {}", ex.getMessage());
            return null;
        }
    }

    private static GeocodeRequest toGeocodeRequest(CreateSavedAddressRequest request) {
        return new GeocodeRequest(
            request.zipCode(),
            request.street(),
            request.number(),
            request.complement(),
            request.district(),
            request.city(),
            request.state()
        );
    }
}
