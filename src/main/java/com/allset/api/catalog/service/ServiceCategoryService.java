package com.allset.api.catalog.service;

import com.allset.api.catalog.dto.CreateServiceCategoryRequest;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import com.allset.api.catalog.dto.UpdateServiceCategoryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ServiceCategoryService {

    ServiceCategoryResponse create(CreateServiceCategoryRequest request);

    Page<ServiceCategoryResponse> findAll(UUID areaId, boolean includeInactive, Pageable pageable);

    ServiceCategoryResponse findById(UUID id);

    ServiceCategoryResponse update(UUID id, UpdateServiceCategoryRequest request);

    void delete(UUID id);
}
