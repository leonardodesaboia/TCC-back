package com.allset.api.catalog.service;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.catalog.dto.ServiceAreaResponse;
import com.allset.api.catalog.dto.UpdateServiceAreaRequest;
import com.allset.api.catalog.exception.ServiceAreaNameAlreadyExistsException;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.mapper.ServiceAreaMapper;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.integration.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAreaServiceImplTest {

    @Mock
    private ServiceAreaRepository serviceAreaRepository;

    @Mock
    private ServiceAreaMapper serviceAreaMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ServiceAreaServiceImpl serviceAreaService;

    @Test
    void createShouldRejectDuplicatedActiveName() {
        CreateServiceAreaRequest request = new CreateServiceAreaRequest("Elétrica");

        when(serviceAreaRepository.existsByNameAndDeletedAtIsNull("Elétrica")).thenReturn(true);

        assertThatThrownBy(() -> serviceAreaService.create(request))
                .isInstanceOf(ServiceAreaNameAlreadyExistsException.class)
                .hasMessageContaining("Elétrica");
    }

    @Test
    void updateShouldPersistChangedFields() {
        UUID id = UUID.randomUUID();
        ServiceArea area = ServiceArea.builder()
                .name("Limpeza")
                .iconKey("catalog-icons/areas/abc.svg")
                .active(true)
                .build();
        area.setId(id);
        area.setCreatedAt(Instant.now());

        UpdateServiceAreaRequest request = new UpdateServiceAreaRequest("Jardinagem", false);
        ServiceAreaResponse response = new ServiceAreaResponse(id, "Jardinagem", null, false, area.getCreatedAt());

        when(serviceAreaRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(area));
        when(serviceAreaRepository.existsByNameAndDeletedAtIsNull("Jardinagem")).thenReturn(false);
        when(serviceAreaRepository.save(area)).thenReturn(area);
        when(serviceAreaMapper.toResponse(area)).thenReturn(response);

        ServiceAreaResponse result = serviceAreaService.update(id, request);

        assertThat(result).isEqualTo(response);
        assertThat(area.getName()).isEqualTo("Jardinagem");
        assertThat(area.isActive()).isFalse();
    }

    @Test
    void deleteShouldSoftDeleteArea() {
        UUID id = UUID.randomUUID();
        ServiceArea area = ServiceArea.builder().name("Pintura").build();
        area.setId(id);

        when(serviceAreaRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(area));

        serviceAreaService.delete(id);

        ArgumentCaptor<ServiceArea> captor = ArgumentCaptor.forClass(ServiceArea.class);
        verify(serviceAreaRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void findByIdShouldFailWhenAreaIsSoftDeletedOrMissing() {
        UUID id = UUID.randomUUID();

        when(serviceAreaRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAreaService.findById(id))
                .isInstanceOf(ServiceAreaNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
