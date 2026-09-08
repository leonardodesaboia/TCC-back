package com.allset.api.address.service;

import com.allset.api.address.domain.CoordinateSource;
import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.address.exception.SavedAddressNotFoundException;
import com.allset.api.address.mapper.SavedAddressMapper;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistência de endereços salvos.
 *
 * <p>Este service <b>não geocodifica</b>. A coordenada chega pronta do cliente,
 * junto com sua procedência, e é gravada como veio. Duas razões:
 *
 * <ul>
 *   <li>o ponto que interessa é o de onde o serviço será prestado — o portão do
 *       condomínio, não o centroide do CEP — e só quem está lá sabe qual é;</li>
 *   <li>chamada HTTP externa dentro de transação JPA segurava conexão do pool
 *       por segundos e ainda podia gravar endereço sem coordenada em silêncio.</li>
 * </ul>
 *
 * <p>Quem quiser uma sugestão de ponto chama {@code POST /api/v1/geocoding/lookup}
 * antes e envia o resultado — explicitamente, com {@code coordinateSource=geocoded}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SavedAddressServiceImpl implements SavedAddressService {

    private static final Logger log = LoggerFactory.getLogger(SavedAddressServiceImpl.class);

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
            .coordinateSource(request.coordinateSource())
            .coordinateAccuracyMeters(request.coordinateAccuracyMeters())
            .coordinateConfidence(request.coordinateConfidence())
            .coordinateConfirmedAt(request.coordinateSource() != null ? Instant.now() : null)
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
            changed(request.street(), address.getStreet())
            || changed(request.number(), address.getNumber())
            || changed(request.district(), address.getDistrict())
            || changed(request.zipCode(), address.getZipCode())
            || changed(request.city(), address.getCity())
            || changed(request.state(), address.getState());

        if (request.label() != null)      { address.setLabel(request.label()); }
        if (request.street() != null)     { address.setStreet(request.street()); }
        if (request.number() != null)     { address.setNumber(request.number()); }
        if (request.complement() != null) { address.setComplement(request.complement()); }
        if (request.district() != null)   { address.setDistrict(request.district()); }
        if (request.city() != null)       { address.setCity(request.city()); }
        if (request.state() != null)      { address.setState(request.state()); }
        if (request.zipCode() != null)    { address.setZipCode(request.zipCode()); }

        boolean coordinateResent = request.coordinateSource() != null;

        if (coordinateResent) {
            address.setLat(request.lat());
            address.setLng(request.lng());
            address.setCoordinateSource(request.coordinateSource());
            address.setCoordinateAccuracyMeters(request.coordinateAccuracyMeters());
            address.setCoordinateConfidence(request.coordinateConfidence());
            address.setCoordinateConfirmedAt(Instant.now());
        } else if (addressFieldsChanged && address.getCoordinateSource() != null) {
            // O endereço escrito mudou e nenhum ponto novo veio junto: a coordenada
            // antiga foi confirmada para outro endereço e não vale mais. Guardamos o
            // valor (serve de ponto de partida no mapa) mas rebaixamos a procedência,
            // o que tira o endereço do Express até alguém reconfirmar o pin.
            log.info("Endereço {} teve campos alterados sem coordenada nova — procedência rebaixada de {} para legacy",
                id, address.getCoordinateSource());
            address.setCoordinateSource(CoordinateSource.legacy);
            address.setCoordinateAccuracyMeters(null);
            address.setCoordinateConfidence(null);
            address.setCoordinateConfirmedAt(null);
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
    private static boolean changed(String supplied, String current) {
        return supplied != null && !supplied.equals(current);
    }

    private SavedAddress findOwnedAddress(UUID userId, UUID id) {
        return savedAddressRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new SavedAddressNotFoundException(id));
    }
}
