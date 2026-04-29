package com.allset.api.offering.service;

import com.allset.api.catalog.exception.ServiceCategoryNotFoundException;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.dto.CreateProfessionalOfferingRequest;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.offering.dto.UpdateProfessionalOfferingRequest;
import com.allset.api.offering.exception.ProfessionalOfferingNotFoundException;
import com.allset.api.offering.mapper.ProfessionalOfferingMapper;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalOfferingServiceImplTest {

    @Mock
    private ProfessionalOfferingRepository professionalOfferingRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @Mock
    private ProfessionalOfferingMapper professionalOfferingMapper;

    @InjectMocks
    private ProfessionalOfferingServiceImpl professionalOfferingService;

    @Test
    void createShouldRequireExistingProfessional() {
        UUID professionalId = UUID.randomUUID();
        CreateProfessionalOfferingRequest request = new CreateProfessionalOfferingRequest(
                UUID.randomUUID(),
                "Instalacao",
                "Descricao",
                PricingType.fixed,
                new BigDecimal("120.00"),
                90
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalOfferingService.create(professionalId, request))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void createShouldRequireExistingCategory() {
        UUID professionalId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreateProfessionalOfferingRequest request = new CreateProfessionalOfferingRequest(
                categoryId,
                "Instalacao",
                "Descricao",
                PricingType.fixed,
                new BigDecimal("120.00"),
                90
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));
        when(serviceCategoryRepository.findByIdAndDeletedAtIsNull(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalOfferingService.create(professionalId, request))
                .isInstanceOf(ServiceCategoryNotFoundException.class)
                .hasMessageContaining(categoryId.toString());
    }

    @Test
    void updateShouldApplyPartialChanges() {
        UUID professionalId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ProfessionalOffering offering = ProfessionalOffering.builder()
                .professionalId(professionalId)
                .categoryId(categoryId)
                .title("Titulo antigo")
                .description("Descricao antiga")
                .pricingType(PricingType.hourly)
                .price(new BigDecimal("80.00"))
                .estimatedDurationMinutes(60)
                .active(true)
                .build();
        offering.setId(offeringId);
        offering.setCreatedAt(Instant.now());

        UpdateProfessionalOfferingRequest request = new UpdateProfessionalOfferingRequest(
                "Titulo novo",
                null,
                PricingType.fixed,
                new BigDecimal("150.00"),
                120,
                false,
                false
        );

        ProfessionalOfferingResponse response = new ProfessionalOfferingResponse(
                offeringId,
                professionalId,
                categoryId,
                "Titulo novo",
                "Descricao antiga",
                PricingType.fixed,
                new BigDecimal("150.00"),
                new BigDecimal("150.00"),
                120,
                false,
                null,
                0,
                offering.getCreatedAt()
        );

        when(professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(offeringId, professionalId))
                .thenReturn(Optional.of(offering));
        when(professionalOfferingRepository.save(offering)).thenReturn(offering);
        when(professionalOfferingMapper.toResponse(offering)).thenReturn(response);

        ProfessionalOfferingResponse result = professionalOfferingService.update(professionalId, offeringId, request);

        assertThat(result).isEqualTo(response);
        assertThat(offering.getTitle()).isEqualTo("Titulo novo");
        assertThat(offering.getDescription()).isEqualTo("Descricao antiga");
        assertThat(offering.getPricingType()).isEqualTo(PricingType.fixed);
        assertThat(offering.getPrice()).isEqualByComparingTo("150.00");
        assertThat(offering.getEstimatedDurationMinutes()).isEqualTo(120);
        assertThat(offering.isActive()).isFalse();
    }

    @Test
    void deleteShouldSoftDeleteOffering() {
        UUID professionalId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        ProfessionalOffering offering = ProfessionalOffering.builder()
                .professionalId(professionalId)
                .categoryId(UUID.randomUUID())
                .title("Servico")
                .pricingType(PricingType.fixed)
                .price(new BigDecimal("99.00"))
                .build();
        offering.setId(offeringId);

        when(professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(offeringId, professionalId))
                .thenReturn(Optional.of(offering));

        professionalOfferingService.delete(professionalId, offeringId);

        ArgumentCaptor<ProfessionalOffering> captor = ArgumentCaptor.forClass(ProfessionalOffering.class);
        verify(professionalOfferingRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void findByIdShouldFailWhenOwnershipDoesNotMatch() {
        UUID professionalId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        when(professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(offeringId, professionalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalOfferingService.findById(professionalId, offeringId))
                .isInstanceOf(ProfessionalOfferingNotFoundException.class)
                .hasMessageContaining(offeringId.toString());
    }
}
