package com.allset.api.catalog.mapper;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ServiceAreaMapper {

    private final StorageRefFactory storageRefFactory;

    public ServiceAreaResponse toResponse(ServiceArea area) {
        return new ServiceAreaResponse(
                area.getId(),
                area.getName(),
                storageRefFactory.from(StorageBucket.CATALOG_ICONS, area.getIconKey()),
                area.isActive(),
                area.getCreatedAt()
        );
    }

    public List<ServiceAreaResponse> toResponseList(List<ServiceArea> areas) {
        return areas.stream().map(this::toResponse).toList();
    }
}
