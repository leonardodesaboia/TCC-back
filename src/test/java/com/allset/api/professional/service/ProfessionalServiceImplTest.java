package com.allset.api.professional.service;

import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.CreateProfessionalRequest;
import com.allset.api.professional.dto.ProfessionalResponse;
import com.allset.api.professional.dto.ProfessionalSpecialtyRequest;
import com.allset.api.professional.dto.VerifyProfessionalRequest;
import com.allset.api.professional.exception.ProfessionalAlreadyExistsException;
import com.allset.api.professional.mapper.ProfessionalMapper;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalServiceImplTest {

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfessionalMapper professionalMapper;

    @InjectMocks
    private ProfessionalServiceImpl professionalService;

    @Test
    void createShouldRequireExistingUser() {
        UUID userId = UUID.randomUUID();
        CreateProfessionalRequest request = new CreateProfessionalRequest(
                userId,
                "Bio",
                (short) 5,
                new BigDecimal("80.00"),
                List.of(new ProfessionalSpecialtyRequest(UUID.randomUUID(), (short) 5, new BigDecimal("80.00")))
        );

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalService.create(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void createShouldRejectSecondProfileForSameUser() {
        UUID userId = UUID.randomUUID();
        CreateProfessionalRequest request = new CreateProfessionalRequest(
                userId,
                "Bio",
                (short) 5,
                new BigDecimal("80.00"),
                List.of(new ProfessionalSpecialtyRequest(UUID.randomUUID(), (short) 5, new BigDecimal("80.00")))
        );

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(User.builder().build()));
        when(professionalRepository.existsByUserIdAndDeletedAtIsNull(userId)).thenReturn(true);

        assertThatThrownBy(() -> professionalService.create(request))
                .isInstanceOf(ProfessionalAlreadyExistsException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void verifyShouldRequireRejectionReasonWhenRejected() {
        UUID professionalId = UUID.randomUUID();
        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));

        assertThatThrownBy(() -> professionalService.verify(
                professionalId,
                new VerifyProfessionalRequest(VerificationStatus.rejected, " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void verifyShouldClearRejectionReasonWhenApproved() {
        UUID professionalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Professional professional = Professional.builder()
                .userId(userId)
                .verificationStatus(VerificationStatus.rejected)
                .rejectionReason("Documento ilegivel")
                .build();
        professional.setId(professionalId);
        professional.setCreatedAt(Instant.now());
        professional.setUpdatedAt(Instant.now());

        ProfessionalResponse response = new ProfessionalResponse(
                professionalId,
                userId,
                professional.getBio(),
                professional.getYearsOfExperience(),
                professional.getBaseHourlyRate(),
                List.of(),
                VerificationStatus.approved,
                null,
                professional.isGeoActive(),
                professional.getSubscriptionPlanId(),
                professional.getSubscriptionExpiresAt(),
                null,
                0,
                professional.getCreatedAt(),
                professional.getUpdatedAt()
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(professionalRepository.save(professional)).thenReturn(professional);
        when(professionalMapper.toResponse(professional)).thenReturn(response);

        ProfessionalResponse result = professionalService.verify(
                professionalId,
                new VerifyProfessionalRequest(VerificationStatus.approved, "nao deve permanecer")
        );

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.approved);
        assertThat(professional.getVerificationStatus()).isEqualTo(VerificationStatus.approved);
        assertThat(professional.getRejectionReason()).isNull();
    }

    @Test
    void deleteShouldSoftDeleteProfessional() {
        UUID professionalId = UUID.randomUUID();
        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));

        professionalService.delete(professionalId);

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }
}
