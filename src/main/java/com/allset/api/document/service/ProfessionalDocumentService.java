package com.allset.api.document.service;

import com.allset.api.document.dto.CreateProfessionalDocumentRequest;
import com.allset.api.document.dto.ProfessionalDocumentResponse;

import java.util.List;
import java.util.UUID;

public interface ProfessionalDocumentService {

    ProfessionalDocumentResponse create(UUID professionalId, CreateProfessionalDocumentRequest request);

    List<ProfessionalDocumentResponse> findAllByProfessional(UUID professionalId);

    void delete(UUID professionalId, UUID id);
}
