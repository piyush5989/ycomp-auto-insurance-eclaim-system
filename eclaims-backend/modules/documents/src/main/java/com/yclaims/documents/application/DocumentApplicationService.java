package com.yclaims.documents.application;

import com.yclaims.documents.config.StorageProperties;
import com.yclaims.documents.domain.model.DocumentType;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import com.yclaims.documents.infrastructure.persistence.DocumentEntity;
import com.yclaims.documents.infrastructure.persistence.DocumentJpaRepository;
import com.yclaims.documents.presentation.dto.DocumentMetadataResponse;
import com.yclaims.kernel.exception.DomainException;
import com.yclaims.kernel.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentApplicationService {

    private final DocumentStoragePort storagePort;
    private final DocumentJpaRepository documentRepository;
    private final StorageProperties storageProperties;

    @Transactional
    public DocumentMetadataResponse uploadDocument(UUID claimId, String documentType,
                                                    MultipartFile file, String userId,
                                                    String correlationId) throws Exception {
        validateFile(claimId, file, correlationId);

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
        log.info("[{}] Document {} ({} bytes, {}) uploaded for claim {}",
                correlationId, documentId, file.getSize(), file.getContentType(), claimId);

        return toResponse(entity);
    }

    private void validateFile(UUID claimId, MultipartFile file, String correlationId) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("DOC_EMPTY", "Uploaded file is empty.");
        }

        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            long limitMb = storageProperties.getMaxFileSizeBytes() / (1024 * 1024);
            throw new DomainException("DOC_TOO_LARGE",
                    "File size exceeds the maximum allowed limit of " + limitMb + " MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !storageProperties.getAllowedContentTypes().contains(contentType)) {
            throw new DomainException("DOC_INVALID_TYPE",
                    "File type '" + contentType + "' is not allowed. " +
                    "Accepted types: JPEG, PNG, WebP, GIF, MP4, MOV, PDF, DOC, DOCX.");
        }

        long existingCount = documentRepository.countByClaimId(claimId);
        if (existingCount >= storageProperties.getMaxDocumentsPerClaim()) {
            throw new DomainException("DOC_LIMIT_REACHED",
                    "Maximum of " + storageProperties.getMaxDocumentsPerClaim() +
                    " documents per claim has been reached.");
        }

        log.debug("[{}] File validation passed: {} ({} bytes, {})",
                correlationId, file.getOriginalFilename(), file.getSize(), contentType);
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

    @Transactional(readOnly = true)
    public Resource loadAsResource(String storageKey, String correlationId) {
        return resolveFileByStorageKey(storageKey).resource();
    }

    /**
     * Stream a document by id — use this from the API so clients send a Bearer token (SPA-friendly).
     * Raw {@code /download/{storageKey}} URLs fail in a new browser tab because no JWT is attached.
     */
    @Transactional(readOnly = true)
    public DocumentFileStream streamDocumentById(UUID documentId, String correlationId) {
        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
        DocumentFileStream stream = resolveFileByStorageKey(entity.getStorageKey());
        String contentType = entity.getContentType() != null && !entity.getContentType().isBlank()
                ? entity.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return new DocumentFileStream(stream.resource(), contentType, entity.getFilename());
    }

    private DocumentFileStream resolveFileByStorageKey(String storageKey) {
        Path root = Paths.get(storageProperties.getPath()).toAbsolutePath().normalize();
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root)) {
            throw new DomainException("DOC_INVALID_PATH", "Resolved path escapes storage root.");
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return new DocumentFileStream(resource, MediaType.APPLICATION_OCTET_STREAM_VALUE, storageKey);
            }
            throw new NotFoundException("Document file", storageKey);
        } catch (MalformedURLException e) {
            throw new NotFoundException("Document file", storageKey);
        }
    }

    @Transactional
    public void deleteDocument(UUID documentId, String requestingUserId, String correlationId) {
        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
        storagePort.delete(entity.getStorageKey());
        documentRepository.deleteById(documentId);
        log.info("[{}] Document {} deleted by user {}", correlationId, documentId, requestingUserId);
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
