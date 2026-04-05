package com.allset.api.document.service;

import com.allset.api.document.domain.DocType;
import com.allset.api.document.domain.ProfessionalDocument;
import com.allset.api.document.dto.CreateProfessionalDocumentRequest;
import com.allset.api.document.dto.ProfessionalDocumentResponse;
import com.allset.api.document.exception.ProfessionalDocumentNotFoundException;
import com.allset.api.document.mapper.ProfessionalDocumentMapper;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ProfessionalDocumentServiceImpl professionalDocumentService;

    @Test
    void createShouldRequireExistingProfessional() {
        UUID professionalId = UUID.randomUUID();
        CreateProfessionalDocumentRequest request = new CreateProfessionalDocumentRequest(DocType.rg, "https://cdn/doc.pdf");

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalDocumentService.create(professionalId, request))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void createShouldPersistDocumentForProfessional() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        CreateProfessionalDocumentRequest request = new CreateProfessionalDocumentRequest(DocType.cnh, "https://cdn/cnh.pdf");

        ProfessionalDocument saved = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.cnh)
                .fileUrl("https://cdn/cnh.pdf")
                .uploadedAt(Instant.now())
                .verified(false)
                .build();
        saved.setId(documentId);

        ProfessionalDocumentResponse response = new ProfessionalDocumentResponse(
                documentId,
                professionalId,
                DocType.cnh,
                "https://cdn/cnh.pdf",
                saved.getUploadedAt(),
                false
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId))
                .thenReturn(Optional.of(Professional.builder().userId(UUID.randomUUID()).build()));
        when(professionalDocumentRepository.save(any(ProfessionalDocument.class))).thenReturn(saved);
        when(professionalDocumentMapper.toResponse(saved)).thenReturn(response);

        ProfessionalDocumentResponse result = professionalDocumentService.create(professionalId, request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void deleteShouldFailWhenDocumentDoesNotBelongToProfessional() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(professionalDocumentRepository.findByIdAndProfessionalId(documentId, professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalDocumentService.delete(professionalId, documentId))
                .isInstanceOf(ProfessionalDocumentNotFoundException.class)
                .hasMessageContaining(documentId.toString());
    }

    @Test
    void deleteShouldRemoveMatchedDocument() {
        UUID professionalId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ProfessionalDocument document = ProfessionalDocument.builder()
                .professionalId(professionalId)
                .docType(DocType.rg)
                .fileUrl("https://cdn/rg.pdf")
                .build();
        document.setId(documentId);

        when(professionalDocumentRepository.findByIdAndProfessionalId(documentId, professionalId))
                .thenReturn(Optional.of(document));

        professionalDocumentService.delete(professionalId, documentId);

        verify(professionalDocumentRepository).delete(document);
    }
}
