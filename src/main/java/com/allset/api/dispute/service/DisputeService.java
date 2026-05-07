package com.allset.api.dispute.service;

import com.allset.api.dispute.domain.DisputeStatus;
import com.allset.api.dispute.dto.AddTextEvidenceRequest;
import com.allset.api.dispute.dto.DisputeEvidenceResponse;
import com.allset.api.dispute.dto.DisputeResponse;
import com.allset.api.dispute.dto.OpenDisputeRequest;
import com.allset.api.dispute.dto.ResolveDisputeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DisputeService {

    /**
     * Abre uma disputa para o pedido. Apenas o cliente dono do pedido pode chamar.
     * Transita o pedido de {@code completed_by_pro} para {@code disputed}.
     */
    DisputeResponse openDispute(UUID orderId, UUID clientId, OpenDisputeRequest request);

    DisputeResponse getById(UUID disputeId, UUID requesterId, String requesterRole);

    DisputeResponse getByOrderId(UUID orderId, UUID requesterId, String requesterRole);

    Page<DisputeResponse> listAll(DisputeStatus status, Pageable pageable);

    DisputeResponse markUnderReview(UUID disputeId, UUID adminId);

    DisputeResponse resolve(UUID disputeId, UUID adminId, ResolveDisputeRequest request);

    DisputeEvidenceResponse addTextEvidence(UUID disputeId, UUID senderId, String senderRole,
                                             AddTextEvidenceRequest request);

    DisputeEvidenceResponse addPhotoEvidence(UUID disputeId, UUID senderId, String senderRole,
                                              MultipartFile file, String caption);

    List<DisputeEvidenceResponse> listEvidences(UUID disputeId, UUID requesterId, String requesterRole);
}
