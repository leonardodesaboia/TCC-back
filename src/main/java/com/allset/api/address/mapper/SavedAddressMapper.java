package com.allset.api.address.mapper;

import com.allset.api.address.domain.CoordinateTrust;
import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.dto.SavedAddressResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converte {@link SavedAddress} em {@link SavedAddressResponse}.
 * Responsabilidade única: mapeamento de campos. Sem lógica de negócio.
 */
@Component
public class SavedAddressMapper {

    public SavedAddressResponse toResponse(SavedAddress address) {
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
            CoordinateTrust.isExpressReady(address),
            address.isDefault(),
            address.getCreatedAt(),
            address.getUpdatedAt()
        );
    }

    public List<SavedAddressResponse> toResponseList(List<SavedAddress> addresses) {
        return addresses.stream()
            .map(this::toResponse)
            .toList();
    }
}
