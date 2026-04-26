package com.allset.api.catalog.service;

import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.dto.CreateServiceCategoryRequest;
import com.allset.api.catalog.dto.ServiceCategoryResponse;
import com.allset.api.catalog.dto.UpdateServiceCategoryRequest;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.mapper.ServiceCategoryMapper;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.shared.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCategoryServiceImplTest {

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @Mock
    private ServiceAreaRepository serviceAreaRepository;

    @Mock
    private ServiceCategoryMapper serviceCategoryMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ServiceCategoryServiceImpl serviceCategoryService;

    @Test
    void createShouldRequireExistingArea() {
        UUID areaId = UUID.randomUUID();

        when(serviceAreaRepository.findByIdAndDeletedAtIsNull(areaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceCategoryService.create(
                new CreateServiceCategoryRequest(areaId, "Instalacao")))
                .isInstanceOf(ServiceAreaNotFoundException.class)
                .hasMessageContaining(areaId.toString());
    }

    @Test
    void findAllShouldReturnOnlyActiveCategoriesByDefault() {
        UUID areaId = UUID.randomUUID();
        ServiceCategory category = category(areaId);
        ServiceCategoryResponse response = response(category);

        when(serviceCategoryRepository.findAllByAreaIdAndActiveTrueAndDeletedAtIsNull(areaId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(category)));
        when(serviceCategoryMapper.toResponse(category)).thenReturn(response);

        var page = serviceCategoryService.findAll(areaId, false, PageRequest.of(0, 20));

        assertThat(page.getContent()).containsExactly(response);
    }

    @Test
    void deleteShouldSoftDeleteCategory() {
        UUID areaId = UUID.randomUUID();
        ServiceCategory category = category(areaId);

        when(serviceCategoryRepository.findByIdAndDeletedAtIsNull(category.getId())).thenReturn(Optional.of(category));

        serviceCategoryService.delete(category.getId());

        ArgumentCaptor<ServiceCategory> captor = ArgumentCaptor.forClass(ServiceCategory.class);
        verify(serviceCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void updateShouldPersistChangedFields() {
        UUID areaId = UUID.randomUUID();
        ServiceCategory category = category(areaId);

        when(serviceCategoryRepository.findByIdAndDeletedAtIsNull(category.getId())).thenReturn(Optional.of(category));
        when(serviceCategoryRepository.save(category)).thenReturn(category);
        when(serviceCategoryMapper.toResponse(category)).thenAnswer(invocation -> response(invocation.getArgument(0)));

        ServiceCategoryResponse response = serviceCategoryService.update(category.getId(),
                new UpdateServiceCategoryRequest("Reformas", false));

        assertThat(response.name()).isEqualTo("Reformas");
        assertThat(response.active()).isFalse();
    }

    private ServiceCategory category(UUID areaId) {
        ServiceCategory category = ServiceCategory.builder()
                .areaId(areaId)
                .name("Instalacao")
                .active(true)
                .build();
        category.setId(UUID.randomUUID());
        category.setCreatedAt(Instant.now());
        return category;
    }

    private ServiceCategoryResponse response(ServiceCategory category) {
        return new ServiceCategoryResponse(
                category.getId(),
                category.getAreaId(),
                category.getName(),
                null,
                category.isActive(),
                category.getCreatedAt()
        );
    }
}
