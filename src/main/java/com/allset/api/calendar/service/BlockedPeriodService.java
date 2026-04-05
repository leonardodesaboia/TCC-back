package com.allset.api.calendar.service;

import com.allset.api.calendar.dto.BlockedPeriodResponse;
import com.allset.api.calendar.dto.CreateBlockedPeriodRequest;

import java.util.List;
import java.util.UUID;

public interface BlockedPeriodService {

    BlockedPeriodResponse create(UUID professionalId, CreateBlockedPeriodRequest request);

    List<BlockedPeriodResponse> findAllByProfessional(UUID professionalId);

    void delete(UUID professionalId, UUID id);
}
