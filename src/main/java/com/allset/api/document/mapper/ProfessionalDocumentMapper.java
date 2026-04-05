package com.allset.api.document.mapper;

import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfessionalDocumentMapper {

    public ProfessionalDocumentResponse toResponse(ProfessionalDocument document) {
        return new ProfessionalDocumentResponse(
                document.getId(),
                document.getProfessionalId(),
                document.getDocType(),
                document.getFileUrl(),
                document.getUploadedAt(),
                document.isVerified()
        );
    }

    public List<ProfessionalDocumentResponse> toResponseList(List<ProfessionalDocument> documents) {
        return documents.stream().map(this::toResponse).toList();
    }
}
