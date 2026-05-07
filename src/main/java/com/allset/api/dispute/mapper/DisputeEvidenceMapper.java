package com.allset.api.dispute.mapper;

import com.allset.api.dispute.domain.DisputeEvidence;
import com.allset.api.dispute.dto.DisputeEvidenceResponse;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DisputeEvidenceMapper {

    private final StorageRefFactory storageRefFactory;

    public DisputeEvidenceResponse toResponse(DisputeEvidence evidence) {
        return new DisputeEvidenceResponse(
                evidence.getId(),
                evidence.getDisputeId(),
                evidence.getSenderId(),
                evidence.getEvidenceType(),
                evidence.getContent(),
                storageRefFactory.from(StorageBucket.DISPUTE_EVIDENCES, evidence.getFileKey()),
                evidence.getSentAt()
        );
    }

    public List<DisputeEvidenceResponse> toResponseList(List<DisputeEvidence> evidences) {
        return evidences.stream().map(this::toResponse).toList();
    }
}
