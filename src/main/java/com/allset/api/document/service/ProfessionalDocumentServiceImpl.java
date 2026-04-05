package com.allset.api.document.service;

import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.CreateProfessionalDocumentRequest;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.document.exception.ProfessionalDocumentNotFoundException;
import com.allset.api.document.mapper.ProfessionalDocumentMapper;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
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
public class ProfessionalDocumentServiceImpl implements ProfessionalDocumentService {

    private final ProfessionalDocumentRepository professionalDocumentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProfessionalDocumentMapper professionalDocumentMapper;

    @Override
    public ProfessionalDocumentResponse create(UUID professionalId, CreateProfessionalDocumentRequest request) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        ProfessionalDocument document = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(request.docType())
                .fileUrl(request.fileUrl())
                .build();

        return professionalDocumentMapper.toResponse(professionalDocumentRepository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalDocumentResponse> findAllByProfessional(UUID professionalId) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        return professionalDocumentMapper.toResponseList(
                professionalDocumentRepository.findAllByProfessionalId(professionalId)
        );
    }

    @Override
    public void delete(UUID professionalId, UUID id) {
        ProfessionalDocument document = professionalDocumentRepository.findByIdAndProfessionalId(id, professionalId)
                .orElseThrow(() -> new ProfessionalDocumentNotFoundException(id));

        professionalDocumentRepository.delete(document);
    }
}
