package com.allset.api.calendar.mapper;

import com.allset.api.calendar.domain.BlockedPeriod;
import com.allset.api.calendar.dto.BlockedPeriodResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BlockedPeriodMapper {

    public BlockedPeriodResponse toResponse(BlockedPeriod period) {
        return new BlockedPeriodResponse(
                period.getId(),
                period.getProfessionalId(),
                period.getBlockType(),
                period.getWeekday(),
                period.getSpecificDate(),
                period.getStartsAt(),
                period.getEndsAt(),
                period.getOrderId(),
                period.getOrderStartsAt(),
                period.getOrderEndsAt(),
                period.getReason(),
                period.getCreatedAt()
        );
    }

    public List<BlockedPeriodResponse> toResponseList(List<BlockedPeriod> periods) {
        return periods.stream().map(this::toResponse).toList();
    }
}
