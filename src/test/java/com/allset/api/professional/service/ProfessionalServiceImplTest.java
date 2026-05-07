package com.allset.api.professional.service;

import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.ProfessionalSpecialty;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.CreateProfessionalRequest;
import com.allset.api.professional.dto.ProfessionalResponse;
import com.allset.api.professional.dto.ProfessionalSpecialtyRequest;
import com.allset.api.professional.dto.UpdateGeoRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalServiceImplTest {

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    @Mock
    private ProfessionalDocumentRepository professionalDocumentRepository;

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
                "Profissional",
                null,
                professional.getBio(),
                professional.getYearsOfExperience(),
                professional.getBaseHourlyRate(),
                List.of(),
                VerificationStatus.approved,
                null,
                professional.isGeoActive(),
                professional.getGeoCapturedAt(),
                professional.getGeoAccuracyMeters(),
                professional.getSubscriptionPlanId(),
                professional.getSubscriptionExpiresAt(),
                null,
                0,
                professional.getCreatedAt(),
                professional.getUpdatedAt()
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(professionalDocumentRepository.countByProfessionalId(professionalId)).thenReturn(2L);
        when(professionalSpecialtyRepository.findAllByProfessionalIdAndDeletedAtIsNullOrderByCreatedAtAsc(professionalId))
                .thenReturn(List.of(ProfessionalSpecialty.builder().professionalId(professionalId).categoryId(UUID.randomUUID()).yearsOfExperience((short) 3).build()));
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

    @Test
    void updateGeoShouldPersistCapturedAtWhenWithinSkewWindow() {
        UUID id = UUID.randomUUID();
        Instant capturedAt = Instant.now().minusSeconds(5);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                new BigDecimal("12.5"),
                capturedAt,
                "device-gps"
        ));

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        Professional saved = captor.getValue();
        assertThat(saved.getGeoCapturedAt()).isEqualTo(capturedAt);
        assertThat(saved.getGeoAccuracyMeters()).isEqualByComparingTo("12.5");
        assertThat(saved.getGeoSource()).isEqualTo("device-gps");
        assertThat(saved.isGeoActive()).isTrue();
    }

    @Test
    void updateGeoShouldFallbackToNowWhenCapturedAtMissing() {
        UUID id = UUID.randomUUID();
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, null, null
        ));
        Instant after = Instant.now();

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt())
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void updateGeoShouldRejectFutureSkewAndUseNow() {
        UUID id = UUID.randomUUID();
        Instant futureBeyondMargin = Instant.now().plusSeconds(60);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, futureBeyondMargin, null
        ));
        Instant after = Instant.now();

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt())
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void updateGeoShouldRejectStaleSkewAndUseNow() {
        UUID id = UUID.randomUUID();
        Instant veryOld = Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, veryOld, null
        ));
        Instant after = Instant.now();

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt())
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void updateGeoShouldAcceptCapturedAtAtFutureBoundary() {
        UUID id = UUID.randomUUID();
        Instant atFutureBoundary = Instant.now().plusSeconds(29);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, atFutureBoundary, null
        ));

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt()).isEqualTo(atFutureBoundary);
    }

    @Test
    void updateGeoShouldRejectCapturedAtJustBeyondFutureBoundary() {
        UUID id = UUID.randomUUID();
        Instant beyondFuture = Instant.now().plusSeconds(45);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, beyondFuture, null
        ));
        Instant after = Instant.now();

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt())
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void updateGeoShouldAcceptCapturedAtAtPastBoundary() {
        UUID id = UUID.randomUUID();
        Instant atPastBoundary = Instant.now().minusSeconds(3590);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, atPastBoundary, null
        ));

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt()).isEqualTo(atPastBoundary);
    }

    @Test
    void updateGeoShouldRejectCapturedAtJustBeyondPastBoundary() {
        UUID id = UUID.randomUUID();
        Instant beyondPast = Instant.now().minusSeconds(3700);
        Professional pro = approvedProfessional(id);
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        professionalService.updateGeo(id, new UpdateGeoRequest(
                true,
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                null, beyondPast, null
        ));
        Instant after = Instant.now();

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        assertThat(captor.getValue().getGeoCapturedAt())
                .isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void updateGeoShouldClearMetadataOnDeactivate() {
        UUID id = UUID.randomUUID();
        Professional pro = approvedProfessional(id);
        pro.setGeoActive(true);
        pro.setGeoCapturedAt(Instant.now());
        pro.setGeoAccuracyMeters(new BigDecimal("8.0"));
        pro.setGeoSource("device-gps");
        when(professionalRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(pro));
        when(professionalRepository.save(any(Professional.class))).thenAnswer(inv -> inv.getArgument(0));

        professionalService.updateGeo(id, new UpdateGeoRequest(
                false, null, null, null, null, null
        ));

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository).save(captor.capture());
        Professional saved = captor.getValue();
        assertThat(saved.isGeoActive()).isFalse();
        assertThat(saved.getGeoCapturedAt()).isNull();
        assertThat(saved.getGeoAccuracyMeters()).isNull();
        assertThat(saved.getGeoSource()).isNull();
    }

    private Professional approvedProfessional(UUID id) {
        Professional p = new Professional();
        p.setId(id);
        p.setUserId(UUID.randomUUID());
        p.setVerificationStatus(VerificationStatus.approved);
        p.setGeoLat(new BigDecimal("-3.731862"));
        p.setGeoLng(new BigDecimal("-38.526669"));
        return p;
    }
}
