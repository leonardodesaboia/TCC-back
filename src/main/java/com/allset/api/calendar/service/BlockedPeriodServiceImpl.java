package com.allset.api.calendar.service;

import com.allset.api.calendar.domain.BlockType;
import com.allset.api.calendar.domain.BlockedPeriod;
import com.allset.api.calendar.dto.BlockedPeriodResponse;
import com.allset.api.calendar.dto.CreateBlockedPeriodRequest;
import com.allset.api.calendar.exception.BlockedPeriodNotFoundException;
import com.allset.api.calendar.mapper.BlockedPeriodMapper;
import com.allset.api.calendar.repository.BlockedPeriodRepository;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockedPeriodServiceImpl implements BlockedPeriodService {

    private final BlockedPeriodRepository blockedPeriodRepository;
    private final ProfessionalRepository professionalRepository;
    private final BlockedPeriodMapper blockedPeriodMapper;

    @Override
    public BlockedPeriodResponse create(UUID professionalId, CreateBlockedPeriodRequest request) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        validateByBlockType(request);

        BlockedPeriod period = BlockedPeriod.builder()
                .professionalId(professionalId)
                .blockType(request.blockType())
                .weekday(request.weekday())
                .specificDate(request.specificDate())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .orderId(request.orderId())
                .orderStartsAt(request.orderStartsAt())
                .orderEndsAt(request.orderEndsAt())
                .reason(request.reason())
                .build();

        return blockedPeriodMapper.toResponse(blockedPeriodRepository.save(period));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedPeriodResponse> findAllByProfessional(UUID professionalId) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        return blockedPeriodMapper.toResponseList(
                blockedPeriodRepository.findAllByProfessionalId(professionalId)
        );
    }

    @Override
    public void delete(UUID professionalId, UUID id) {
        BlockedPeriod period = blockedPeriodRepository.findByIdAndProfessionalId(id, professionalId)
                .orElseThrow(() -> new BlockedPeriodNotFoundException(id));

        blockedPeriodRepository.delete(period);
    }

    private void validateByBlockType(CreateBlockedPeriodRequest request) {
        if (request.blockType() == BlockType.recurring && request.weekday() == null) {
            throw new IllegalArgumentException("weekday é obrigatório para bloqueios recorrentes");
        }
        if (request.blockType() == BlockType.specific_date && request.specificDate() == null) {
            throw new IllegalArgumentException("specificDate é obrigatório para bloqueios de data específica");
        }
        if (request.blockType() == BlockType.order) {
            if (request.orderId() == null || request.orderStartsAt() == null || request.orderEndsAt() == null) {
                throw new IllegalArgumentException("orderId, orderStartsAt e orderEndsAt são obrigatórios para bloqueios por pedido");
            }
        }
        if (request.startsAt() != null && request.endsAt() != null
                && !request.startsAt().isBefore(request.endsAt())) {
            throw new IllegalArgumentException("startsAt deve ser anterior a endsAt");
        }
        if (request.orderStartsAt() != null && request.orderEndsAt() != null
                && !request.orderStartsAt().isBefore(request.orderEndsAt())) {
            throw new IllegalArgumentException("orderStartsAt deve ser anterior a orderEndsAt");
        }
    }
}
