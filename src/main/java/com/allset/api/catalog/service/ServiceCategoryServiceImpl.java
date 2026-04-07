package com.allset.api.catalog.service;

import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.dto.CreateServiceCategoryRequest;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import com.allset.api.catalog.dto.UpdateServiceCategoryRequest;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.exception.ServiceCategoryNotFoundException;
import com.allset.api.catalog.mapper.ServiceCategoryMapper;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceCategoryServiceImpl implements ServiceCategoryService {

    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceCategoryMapper serviceCategoryMapper;

    @Override
    public ServiceCategoryResponse create(CreateServiceCategoryRequest request) {
        serviceAreaRepository.findByIdAndDeletedAtIsNull(request.areaId())
                .orElseThrow(() -> new ServiceAreaNotFoundException(request.areaId()));

        ServiceCategory category = ServiceCategory.builder()
                .areaId(request.areaId())
                .name(request.name())
                .iconUrl(request.iconUrl())
                .build();

        return serviceCategoryMapper.toResponse(serviceCategoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceCategoryResponse> findAll(UUID areaId, boolean includeInactive, Pageable pageable) {
        Page<ServiceCategory> page;
        if (areaId != null && includeInactive) {
            page = serviceCategoryRepository.findAllByAreaIdAndDeletedAtIsNull(areaId, pageable);
        } else if (areaId != null) {
            page = serviceCategoryRepository.findAllByAreaIdAndActiveTrueAndDeletedAtIsNull(areaId, pageable);
        } else if (includeInactive) {
            page = serviceCategoryRepository.findAllByDeletedAtIsNull(pageable);
        } else {
            page = serviceCategoryRepository.findAllByActiveTrueAndDeletedAtIsNull(pageable);
        }
        return page.map(serviceCategoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceCategoryResponse findById(UUID id) {
        return serviceCategoryMapper.toResponse(findActiveById(id));
    }

    @Override
    public ServiceCategoryResponse update(UUID id, UpdateServiceCategoryRequest request) {
        ServiceCategory category = findActiveById(id);

        if (request.name() != null) category.setName(request.name());
        if (request.iconUrl() != null) category.setIconUrl(request.iconUrl());
        if (request.active() != null) category.setActive(request.active());

        return serviceCategoryMapper.toResponse(serviceCategoryRepository.save(category));
    }

    @Override
    public void delete(UUID id) {
        ServiceCategory category = findActiveById(id);
        category.setDeletedAt(Instant.now());
        serviceCategoryRepository.save(category);
    }

    private ServiceCategory findActiveById(UUID id) {
        return serviceCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ServiceCategoryNotFoundException(id));
    }
}
