package com.allset.api.document.service;

import com.allset.api.document.domain.DocType;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProfessionalDocumentService {

    ProfessionalDocumentResponse create(UUID professionalId, DocType docType, MultipartFile file);

    List<ProfessionalDocumentResponse> findAllByProfessional(UUID professionalId);

    void delete(UUID professionalId, UUID id);
}
