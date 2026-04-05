package com.allset.api.catalog.mapper;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceAreaMapper {

    public ServiceAreaResponse toResponse(ServiceArea area) {
        return new ServiceAreaResponse(
                area.getId(),
                area.getName(),
                area.getIconUrl(),
                area.isActive(),
                area.getCreatedAt()
        );
    }

    public List<ServiceAreaResponse> toResponseList(List<ServiceArea> areas) {
        return areas.stream().map(this::toResponse).toList();
    }
}
