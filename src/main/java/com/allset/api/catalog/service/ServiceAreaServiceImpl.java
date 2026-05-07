package com.allset.api.catalog.service;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.catalog.dto.UpdateServiceAreaRequest;
import com.allset.api.catalog.exception.ServiceAreaNameAlreadyExistsException;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.mapper.ServiceAreaMapper;
import com.allset.api.catalog.repository.ServiceAreaRepository;
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
public class ServiceAreaServiceImpl implements ServiceAreaService {

    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceAreaMapper serviceAreaMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ServiceAreaResponse create(CreateServiceAreaRequest request) {
        if (serviceAreaRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new ServiceAreaNameAlreadyExistsException(request.name());
        }

        ServiceArea area = ServiceArea.builder()
                .name(request.name())
                .build();

        return serviceAreaMapper.toResponse(serviceAreaRepository.save(area));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceAreaResponse> findAll(boolean includeInactive, Pageable pageable) {
        if (includeInactive) {
            return serviceAreaRepository.findAllByDeletedAtIsNull(pageable).map(serviceAreaMapper::toResponse);
        }
        return serviceAreaRepository.findAllByActiveTrueAndDeletedAtIsNull(pageable).map(serviceAreaMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceAreaResponse findById(UUID id) {
        return serviceAreaMapper.toResponse(findActiveById(id));
    }

    @Override
    public ServiceAreaResponse update(UUID id, UpdateServiceAreaRequest request) {
        ServiceArea area = findActiveById(id);

        if (request.name() != null && !request.name().equals(area.getName())) {
            if (serviceAreaRepository.existsByNameAndDeletedAtIsNull(request.name())) {
                throw new ServiceAreaNameAlreadyExistsException(request.name());
            }
            area.setName(request.name());
        }
        if (request.active() != null) area.setActive(request.active());

        return serviceAreaMapper.toResponse(serviceAreaRepository.save(area));
    }

    @Override
    public void delete(UUID id) {
        ServiceArea area = findActiveById(id);
        area.setDeletedAt(Instant.now());
        serviceAreaRepository.save(area);
    }

    @Override
    public ServiceAreaResponse setIcon(UUID id, MultipartFile file) {
        ServiceArea area = findActiveById(id);
        String previousKey = area.getIconKey();

        StoredObject stored = storageService.upload(StorageBucket.CATALOG_ICONS, "areas/" + id, file);
        area.setIconKey(stored.key());
        ServiceArea saved = serviceAreaRepository.save(area);

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.CATALOG_ICONS, previousKey));
        }
        return serviceAreaMapper.toResponse(saved);
    }

    @Override
    public ServiceAreaResponse removeIcon(UUID id) {
        ServiceArea area = findActiveById(id);
        String previousKey = area.getIconKey();
        if (previousKey == null || previousKey.isBlank()) {
            return serviceAreaMapper.toResponse(area);
        }
        area.setIconKey(null);
        ServiceArea saved = serviceAreaRepository.save(area);
        eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.CATALOG_ICONS, previousKey));
        return serviceAreaMapper.toResponse(saved);
    }

    private ServiceArea findActiveById(UUID id) {
        return serviceAreaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ServiceAreaNotFoundException(id));
    }
}
