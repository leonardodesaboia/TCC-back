package com.allset.api.catalog.service;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.catalog.dto.UpdateServiceAreaRequest;
import com.allset.api.catalog.exception.ServiceAreaNameAlreadyExistsException;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.mapper.ServiceAreaMapper;
import com.allset.api.catalog.repository.ServiceAreaRepository;
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
public class ServiceAreaServiceImpl implements ServiceAreaService {

    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceAreaMapper serviceAreaMapper;

    @Override
    public ServiceAreaResponse create(CreateServiceAreaRequest request) {
        if (serviceAreaRepository.existsByNameAndDeletedAtIsNull(request.name())) {
            throw new ServiceAreaNameAlreadyExistsException(request.name());
        }

        ServiceArea area = ServiceArea.builder()
                .name(request.name())
                .iconUrl(request.iconUrl())
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
        if (request.iconUrl() != null) area.setIconUrl(request.iconUrl());
        if (request.active() != null) area.setActive(request.active());

        return serviceAreaMapper.toResponse(serviceAreaRepository.save(area));
    }

    @Override
    public void delete(UUID id) {
        ServiceArea area = findActiveById(id);
        area.setDeletedAt(Instant.now());
        serviceAreaRepository.save(area);
    }

    private ServiceArea findActiveById(UUID id) {
        return serviceAreaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ServiceAreaNotFoundException(id));
    }
}
