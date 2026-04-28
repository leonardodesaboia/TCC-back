package com.allset.api.document.service;

import com.allset.api.document.domain.DocType;
import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.document.exception.ProfessionalDocumentNotFoundException;
import com.allset.api.document.mapper.ProfessionalDocumentMapper;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.domain.StoredObject;
import com.allset.api.shared.storage.event.ObjectDeletionRequestedEvent;
import com.allset.api.shared.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalDocumentServiceImplTest {

    @Mock
    private ProfessionalDocumentRepository professionalDocumentRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private ProfessionalDocumentMapper professionalDocumentMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProfessionalDocumentServiceImpl professionalDocumentService;

    @Test
    void createShouldRequireExistingProfessional() {
        UUID professionalId = UUID.randomUUID();
        MultipartFile file = new MockMultipartFile("file", "rg.pdf", "application/pdf", new byte[]{1, 2, 3});

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalDocumentService.create(professionalId, DocType.rg, file))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());

        verify(storageService, never()).upload(any(), any(), any(MultipartFile.class));
    }

    @Test
    void createShouldUploadAndPersistDocument() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MultipartFile file = new MockMultipartFile("file", "cnh.pdf", "application/pdf", new byte[]{1, 2, 3});
        String storedKey = "documents/" + professionalId + "/abc.pdf";

        ProfessionalDocument saved = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.cnh)
                .fileKey(storedKey)
                .uploadedAt(Instant.now())
                .verified(false)
                .build();
        saved.setId(documentId);

        ProfessionalDocumentResponse response = new ProfessionalDocumentResponse(
                documentId,
                professionalId,
                DocType.cnh,
                null,
                saved.getUploadedAt(),
                false
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));
        when(professionalDocumentRepository.findByProfessionalIdAndDocType(professionalId, DocType.cnh))
                .thenReturn(Optional.empty());
        when(storageService.upload(eq(StorageBucket.DOCUMENTS), eq(professionalId.toString()), eq(file)))
                .thenReturn(new StoredObject(StorageBucket.DOCUMENTS, storedKey, "application/pdf", 3L));
        when(professionalDocumentRepository.save(any(ProfessionalDocument.class))).thenReturn(saved);
        when(professionalDocumentMapper.toResponse(saved)).thenReturn(response);

        ProfessionalDocumentResponse result = professionalDocumentService.create(professionalId, DocType.cnh, file);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void createShouldReplaceExistingDocumentOfSameType() {
        UUID professionalId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        MultipartFile file = new MockMultipartFile("file", "document-front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String oldKey = "documents/" + professionalId + "/front-old.jpg";
        String newKey = "documents/" + professionalId + "/front-new.jpg";

        ProfessionalDocument existing = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.document_front)
                .fileKey(oldKey)
                .build();
        existing.setId(existingId);

        ProfessionalDocument saved = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.document_front)
                .fileKey(newKey)
                .uploadedAt(Instant.now())
                .verified(false)
                .build();

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));
        when(professionalDocumentRepository.findByProfessionalIdAndDocType(professionalId, DocType.document_front))
                .thenReturn(Optional.of(existing));
        when(storageService.upload(eq(StorageBucket.DOCUMENTS), eq(professionalId.toString()), eq(file)))
                .thenReturn(new StoredObject(StorageBucket.DOCUMENTS, newKey, "image/jpeg", 3L));
        when(professionalDocumentRepository.save(any(ProfessionalDocument.class))).thenReturn(saved);
        when(professionalDocumentMapper.toResponse(saved)).thenReturn(new ProfessionalDocumentResponse(
                UUID.randomUUID(),
                professionalId,
                DocType.document_front,
                null,
                saved.getUploadedAt(),
                false
        ));

        professionalDocumentService.create(professionalId, DocType.document_front, file);

        verify(professionalDocumentRepository).delete(existing);
        verify(eventPublisher).publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.DOCUMENTS, oldKey));
    }

    @Test
    void deleteShouldFailWhenDocumentDoesNotBelongToProfessional() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(professionalDocumentRepository.findByIdAndProfessionalId(documentId, professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalDocumentService.delete(professionalId, documentId))
                .isInstanceOf(ProfessionalDocumentNotFoundException.class)
                .hasMessageContaining(documentId.toString());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteShouldRemoveDocumentAndPublishStorageEvent() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        String key = "documents/" + professionalId + "/rg.pdf";
        ProfessionalDocument document = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.rg)
                .fileKey(key)
                .build();
        document.setId(documentId);

        when(professionalDocumentRepository.findByIdAndProfessionalId(documentId, professionalId))
                .thenReturn(Optional.of(document));

        professionalDocumentService.delete(professionalId, documentId);

        verify(professionalDocumentRepository).delete(document);
        verify(eventPublisher).publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.DOCUMENTS, key));
    }
}
