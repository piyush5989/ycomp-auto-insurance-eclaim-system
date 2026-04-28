package com.yclaims.documents.application;

import com.yclaims.documents.domain.model.DocumentType;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import com.yclaims.documents.infrastructure.persistence.DocumentEntity;
import com.yclaims.documents.infrastructure.persistence.DocumentJpaRepository;
import com.yclaims.documents.presentation.dto.DocumentMetadataResponse;
import com.yclaims.kernel.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentApplicationService {

    private final DocumentStoragePort storagePort;
    private final DocumentJpaRepository documentRepository;

    @Transactional
    public DocumentMetadataResponse uploadDocument(UUID claimId, String documentType,
                                                    MultipartFile file, String userId,
                                                    String correlationId) throws Exception {
        UUID documentId = UUID.randomUUID();
        String storageKey = storagePort.store(documentId, file.getOriginalFilename(),
                file.getContentType(), file.getInputStream(), file.getSize());

        DocumentEntity entity = new DocumentEntity();
        entity.setId(documentId);
        entity.setClaimId(claimId);
        entity.setDocumentType(DocumentType.valueOf(documentType.toUpperCase()));
        entity.setFilename(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setFileSizeBytes(file.getSize());
        entity.setStorageKey(storageKey);
        entity.setUploadedByUserId(userId);
        entity.setUploadedAt(Instant.now());

        documentRepository.save(entity);
        log.info("[{}] Document {} uploaded for claim {}", correlationId, documentId, claimId);

        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadataResponse> listDocumentsByClaimId(UUID claimId, String correlationId) {
        return documentRepository.findByClaimId(claimId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID documentId, String correlationId) {
        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
        return storagePort.generateDownloadUrl(entity.getStorageKey());
    }

    private DocumentMetadataResponse toResponse(DocumentEntity entity) {
        return DocumentMetadataResponse.builder()
                .documentId(entity.getId())
                .claimId(entity.getClaimId())
                .documentType(entity.getDocumentType().name())
                .filename(entity.getFilename())
                .contentType(entity.getContentType())
                .fileSizeBytes(entity.getFileSizeBytes())
                .downloadUrl(storagePort.generateDownloadUrl(entity.getStorageKey()))
                .uploadedByUserId(entity.getUploadedByUserId())
                .uploadedAt(entity.getUploadedAt())
                .build();
    }
}
