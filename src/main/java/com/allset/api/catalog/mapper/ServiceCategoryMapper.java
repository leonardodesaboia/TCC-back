package com.allset.api.catalog.mapper;

import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceCategoryMapper {

    public ServiceCategoryResponse toResponse(ServiceCategory category) {
        return new ServiceCategoryResponse(
                category.getId(),
                category.getAreaId(),
                category.getName(),
                category.getIconUrl(),
                category.isActive(),
                category.getCreatedAt()
        );
    }

    public List<ServiceCategoryResponse> toResponseList(List<ServiceCategory> categories) {
        return categories.stream().map(this::toResponse).toList();
    }
}
