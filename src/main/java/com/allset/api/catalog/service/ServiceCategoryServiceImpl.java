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
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.event.ObjectDeletionRequestedEvent;
import com.allset.api.integration.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceCategoryServiceImpl implements ServiceCategoryService {

    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceCategoryMapper serviceCategoryMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ServiceCategoryResponse create(CreateServiceCategoryRequest request) {
        serviceAreaRepository.findByIdAndDeletedAtIsNull(request.areaId())
                .orElseThrow(() -> new ServiceAreaNotFoundException(request.areaId()));

        ServiceCategory category = ServiceCategory.builder()
                .areaId(request.areaId())
                .name(request.name())
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
        if (request.active() != null) category.setActive(request.active());

        return serviceCategoryMapper.toResponse(serviceCategoryRepository.save(category));
    }

    @Override
    public void delete(UUID id) {
        ServiceCategory category = findActiveById(id);
        category.setDeletedAt(Instant.now());
        serviceCategoryRepository.save(category);
    }

    @Override
    public ServiceCategoryResponse setIcon(UUID id, MultipartFile file) {
        ServiceCategory category = findActiveById(id);
        String previousKey = category.getIconKey();

        StoredObject stored = storageService.upload(StorageBucket.CATALOG_ICONS, "categories/" + id, file);
        category.setIconKey(stored.key());
        ServiceCategory saved = serviceCategoryRepository.save(category);

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.CATALOG_ICONS, previousKey));
        }
        return serviceCategoryMapper.toResponse(saved);
    }

    @Override
    public ServiceCategoryResponse removeIcon(UUID id) {
        ServiceCategory category = findActiveById(id);
        String previousKey = category.getIconKey();
        if (previousKey == null || previousKey.isBlank()) {
            return serviceCategoryMapper.toResponse(category);
        }
        category.setIconKey(null);
        ServiceCategory saved = serviceCategoryRepository.save(category);
        eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.CATALOG_ICONS, previousKey));
        return serviceCategoryMapper.toResponse(saved);
    }

    private ServiceCategory findActiveById(UUID id) {
        return serviceCategoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ServiceCategoryNotFoundException(id));
    }
}
