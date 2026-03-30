    package com.allset.api.catalog.service;

import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.catalog.dto.UpdateServiceAreaRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ServiceAreaService {

    ServiceAreaResponse create(CreateServiceAreaRequest request);

    Page<ServiceAreaResponse> findAll(boolean includeInactive, Pageable pageable);

    ServiceAreaResponse findById(UUID id);

    ServiceAreaResponse update(UUID id, UpdateServiceAreaRequest request);

    void delete(UUID id);
}
