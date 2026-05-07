package com.allset.api.catalog.mapper;

import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ServiceCategoryMapper {

    private final StorageRefFactory storageRefFactory;

    public ServiceCategoryResponse toResponse(ServiceCategory category) {
        return new ServiceCategoryResponse(
                category.getId(),
                category.getAreaId(),
                category.getName(),
                storageRefFactory.from(StorageBucket.CATALOG_ICONS, category.getIconKey()),
                category.isActive(),
                category.getCreatedAt()
        );
    }

    public List<ServiceCategoryResponse> toResponseList(List<ServiceCategory> categories) {
        return categories.stream().map(this::toResponse).toList();
    }
}
