package com.allset.api.dispute.mapper;

import com.allset.api.dispute.domain.Dispute;
import com.allset.api.dispute.dto.DisputeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DisputeMapper {

    /**
     * Mapeia a disputa para resposta. O parametro {@code includeAdminNotes} controla
     * se as notas internas sao expostas — apenas admins devem receber esse campo.
     */
    public DisputeResponse toResponse(Dispute dispute, boolean includeAdminNotes) {
        return new DisputeResponse(
                dispute.getId(),
                dispute.getOrderId(),
                dispute.getOpenedBy(),
                dispute.getReason(),
                dispute.getStatus(),
                dispute.getResolution(),
                dispute.getClientRefundAmount(),
                dispute.getProfessionalAmount(),
                dispute.getResolvedBy(),
                dispute.getResolvedAt(),
                dispute.getOpenedAt(),
                includeAdminNotes ? dispute.getAdminNotes() : null
        );
    }

    public List<DisputeResponse> toResponseList(List<Dispute> disputes, boolean includeAdminNotes) {
        return disputes.stream()
                .map(d -> toResponse(d, includeAdminNotes))
                .toList();
    }
}
