package com.allset.api.document.service;

import com.allset.api.document.domain.DocType;
import com.allset.api.document.domain.DocumentSide;
import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.document.exception.ProfessionalDocumentNotFoundException;
import com.allset.api.document.mapper.ProfessionalDocumentMapper;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.event.ObjectDeletionRequestedEvent;
import com.allset.api.integration.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfessionalDocumentServiceImpl implements ProfessionalDocumentService {

    private final ProfessionalDocumentRepository professionalDocumentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProfessionalDocumentMapper professionalDocumentMapper;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ProfessionalDocumentResponse create(UUID professionalId, DocType docType, DocumentSide docSide, MultipartFile file) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        StoredObject stored = storageService.upload(StorageBucket.DOCUMENTS, professionalId.toString(), file);

        String previousKey = null;
        var existing = professionalDocumentRepository.findByProfessionalIdAndDocTypeAndDocSide(professionalId, docType, docSide);
        if (existing.isPresent()) {
            if (professional.getVerificationStatus() != VerificationStatus.rejected) {
                throw new IllegalArgumentException("Documento ja enviado e bloqueado para alteracao enquanto a verificacao nao for rejeitada");
            }
            previousKey = existing.get().getFileKey();
            professionalDocumentRepository.delete(existing.get());
        }

        ProfessionalDocument document = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(docType)
                .docSide(docSide)
                .fileKey(stored.key())
                .build();

        ProfessionalDocumentResponse response = professionalDocumentMapper.toResponse(
                professionalDocumentRepository.save(document)
        );

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.DOCUMENTS, previousKey));
        }

        return response;
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
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        if (professional.getVerificationStatus() != VerificationStatus.rejected) {
            throw new IllegalArgumentException(
                    "Documento bloqueado para exclusão enquanto a verificação não for rejeitada");
        }

        ProfessionalDocument document = professionalDocumentRepository.findByIdAndProfessionalId(id, professionalId)
                .orElseThrow(() -> new ProfessionalDocumentNotFoundException(id));

        String key = document.getFileKey();
        professionalDocumentRepository.delete(document);

        if (key != null && !key.isBlank()) {
            eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.DOCUMENTS, key));
        }
    }
}
