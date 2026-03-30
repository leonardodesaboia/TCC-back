package com.allset.api.calendar.service;

import com.allset.api.calendar.domain.BlockType;
import com.allset.api.calendar.domain.BlockedPeriod;
import com.allset.api.calendar.dto.BlockedPeriodResponse;
import com.allset.api.calendar.dto.CreateBlockedPeriodRequest;
import com.allset.api.calendar.exception.BlockedPeriodNotFoundException;
import com.allset.api.calendar.mapper.BlockedPeriodMapper;
import com.allset.api.calendar.repository.BlockedPeriodRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockedPeriodServiceImplTest {

    @Mock
    private BlockedPeriodRepository blockedPeriodRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private BlockedPeriodMapper blockedPeriodMapper;

    @InjectMocks
    private BlockedPeriodServiceImpl blockedPeriodService;

    @Test
    void createShouldRequireExistingProfessional() {
        UUID professionalId = UUID.randomUUID();
        CreateBlockedPeriodRequest request = new CreateBlockedPeriodRequest(
                BlockType.recurring,
                (short) 1,
                null,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                null,
                null,
                null,
                "Agenda"
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockedPeriodService.create(professionalId, request))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void createShouldValidateRecurringBlockType() {
        UUID professionalId = UUID.randomUUID();
        CreateBlockedPeriodRequest request = new CreateBlockedPeriodRequest(
                BlockType.recurring,
                null,
                null,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0),
                null,
                null,
                null,
                "Agenda"
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> blockedPeriodService.create(professionalId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekday");
    }

    @Test
    void createShouldValidateOrderBlockType() {
        UUID professionalId = UUID.randomUUID();
        CreateBlockedPeriodRequest request = new CreateBlockedPeriodRequest(
                BlockType.order,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                "Pedido"
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> blockedPeriodService.create(professionalId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    void createShouldPersistSpecificDateBlock() {
        UUID professionalId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        CreateBlockedPeriodRequest request = new CreateBlockedPeriodRequest(
                BlockType.specific_date,
                null,
                LocalDate.of(2026, 4, 10),
                LocalTime.of(14, 0),
                LocalTime.of(18, 0),
                null,
                null,
                null,
                "Indisponível"
        );

        BlockedPeriod blockedPeriod = BlockedPeriod.builder()
                .professionalId(professionalId)
                .blockType(BlockType.specific_date)
                .specificDate(LocalDate.of(2026, 4, 10))
                .startsAt(LocalTime.of(14, 0))
                .endsAt(LocalTime.of(18, 0))
                .reason("Indisponível")
                .createdAt(Instant.now())
                .build();
        blockedPeriod.setId(blockId);

        BlockedPeriodResponse response = new BlockedPeriodResponse(
                blockId,
                professionalId,
                BlockType.specific_date,
                null,
                blockedPeriod.getSpecificDate(),
                blockedPeriod.getStartsAt(),
                blockedPeriod.getEndsAt(),
                null,
                null,
                null,
                "Indisponível",
                blockedPeriod.getCreatedAt()
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));
        when(blockedPeriodRepository.save(any(BlockedPeriod.class))).thenReturn(blockedPeriod);
        when(blockedPeriodMapper.toResponse(blockedPeriod)).thenReturn(response);

        BlockedPeriodResponse result = blockedPeriodService.create(professionalId, request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void deleteShouldFailWhenBlockDoesNotBelongToProfessional() {
        UUID professionalId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockedPeriodRepository.findByIdAndProfessionalId(blockId, professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockedPeriodService.delete(professionalId, blockId))
                .isInstanceOf(BlockedPeriodNotFoundException.class)
                .hasMessageContaining(blockId.toString());
    }

    @Test
    void deleteShouldRemoveBlock() {
        UUID professionalId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        BlockedPeriod blockedPeriod = BlockedPeriod.builder()
                .professionalId(professionalId)
                .blockType(BlockType.recurring)
                .weekday((short) 2)
                .build();
        blockedPeriod.setId(blockId);

        when(blockedPeriodRepository.findByIdAndProfessionalId(blockId, professionalId))
                .thenReturn(Optional.of(blockedPeriod));

        blockedPeriodService.delete(professionalId, blockId);

        verify(blockedPeriodRepository).delete(blockedPeriod);
    }
}
