package com.allset.api.document.mapper;

import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.service.StorageRefFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfessionalDocumentMapper {

    private final StorageRefFactory storageRefFactory;

    public ProfessionalDocumentResponse toResponse(ProfessionalDocument document) {
        return new ProfessionalDocumentResponse(
                document.getId(),
                document.getProfessionalId(),
                document.getDocType(),
                document.getDocSide(),
                storageRefFactory.from(StorageBucket.DOCUMENTS, document.getFileKey()),
                document.getUploadedAt(),
                document.isVerified()
        );
    }

    public List<ProfessionalDocumentResponse> toResponseList(List<ProfessionalDocument> documents) {
        return documents.stream().map(this::toResponse).toList();
    }
}
