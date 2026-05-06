package com.yclaims.documents.application;

import com.yclaims.documents.config.StorageProperties;
import com.yclaims.documents.domain.model.DocumentStatus;
import com.yclaims.documents.domain.model.DocumentType;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import com.yclaims.documents.infrastructure.persistence.DocumentAuditLogEntity;
import com.yclaims.documents.infrastructure.persistence.DocumentAuditLogJpaRepository;
import com.yclaims.documents.infrastructure.persistence.DocumentEntity;
import com.yclaims.documents.infrastructure.persistence.DocumentJpaRepository;
import com.yclaims.documents.presentation.dto.DocumentMetadataResponse;
import com.yclaims.kernel.exception.DomainException;
import com.yclaims.kernel.exception.NotFoundException;
import com.yclaims.kernel.security.UserContext;
import com.yclaims.kernel.security.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentApplicationService {

    private final DocumentStoragePort storagePort;
    private final DocumentJpaRepository documentRepository;
    private final DocumentAuditLogJpaRepository auditLogRepository;
    private final StorageProperties storageProperties;

    // ─── Role-based access policy ────────────────────────────────────────────
    // Defines which document types each role may READ or WRITE.
    // CASE_MANAGER and AUDITOR have unrestricted access to all types.
    private static final Map<DocumentType, Set<String>> WRITE_ROLES = Map.of(
            DocumentType.REPAIR_ESTIMATE,           Set.of("ROLE_WORKSHOP", "ROLE_ADJUSTOR"),
            DocumentType.INVOICE,                   Set.of("ROLE_WORKSHOP"),
            DocumentType.WORKSHOP_PROGRESS_PHOTO,   Set.of("ROLE_WORKSHOP"),
            DocumentType.WORKSHOP_PROGRESS_VIDEO,   Set.of("ROLE_WORKSHOP"),
            DocumentType.WORKSHOP_BEFORE_PHOTO,     Set.of("ROLE_WORKSHOP"),
            DocumentType.WORKSHOP_AFTER_PHOTO,      Set.of("ROLE_WORKSHOP")
    );

    private static final Set<DocumentType> CUSTOMER_RESTRICTED = EnumSet.of(
            DocumentType.MEDICAL_REPORT,
            DocumentType.DRIVING_LICENSE
    );

    // ─── Upload ──────────────────────────────────────────────────────────────

    @Transactional
    public DocumentMetadataResponse uploadDocument(UUID claimId, String documentType,
                                                   MultipartFile file, String userId,
                                                   String correlationId) throws Exception {
        DocumentType type = parseType(documentType);
        UserContext actor = UserContextHolder.require();
        checkWriteAccess(type, actor);
        validateFile(claimId, file, correlationId);

        byte[] bytes = file.getBytes();
        String checksum = sha256Hex(bytes);

        UUID documentId = UUID.randomUUID();
        String storageKey = storagePort.store(documentId, file.getOriginalFilename(),
                file.getContentType(), new ByteArrayInputStream(bytes), bytes.length);

        // Check whether a previous active version exists — if so, supersede it
        int version = 1;
        UUID parentId = null;
        var existing = documentRepository.findFirstByClaimIdAndDocumentTypeAndStatusOrderByVersionDesc(
                claimId, type, DocumentStatus.ACTIVE);
        if (existing.isPresent()) {
            DocumentEntity prev = existing.get();
            prev.setStatus(DocumentStatus.SUPERSEDED);
            documentRepository.save(prev);
            version = prev.getVersion() + 1;
            parentId = prev.getId();
            audit(prev.getId(), claimId, "SUPERSEDED", actor, correlationId,
                    "replacedBy=" + documentId);
            log.info("[{}] Document {} superseded by new version {}", correlationId, prev.getId(), documentId);
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setId(documentId);
        entity.setClaimId(claimId);
        entity.setDocumentType(type);
        entity.setFilename(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setFileSizeBytes(bytes.length);
        entity.setStorageKey(storageKey);
        entity.setUploadedByUserId(userId);
        entity.setUploadedAt(Instant.now());
        entity.setVersion(version);
        entity.setParentId(parentId);
        entity.setStatus(DocumentStatus.ACTIVE);
        entity.setChecksumSha256(checksum);

        documentRepository.save(entity);

        String action = version == 1 ? "UPLOADED" : "VERSION_ADDED";
        audit(documentId, claimId, action, actor, correlationId,
                "filename=" + file.getOriginalFilename() + ";version=" + version + ";checksum=" + checksum);

        log.info("[{}] Document {} v{} ({} bytes, {}) uploaded for claim {}",
                correlationId, documentId, version, bytes.length, file.getContentType(), claimId);
        return toResponse(entity);
    }

    // ─── List ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentMetadataResponse> listDocumentsByClaimId(UUID claimId, String correlationId) {
        UserContext actor = UserContextHolder.require();
        boolean isAuditor = actor.isAuditor() || actor.isCaseManager();
        List<DocumentEntity> docs = isAuditor
                ? documentRepository.findByClaimId(claimId)
                : documentRepository.findByClaimIdAndStatus(claimId, DocumentStatus.ACTIVE);
        return docs.stream()
                .filter(d -> canRead(d.getDocumentType(), actor))
                .map(this::toResponse)
                .toList();
    }

    // ─── Download URL ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID documentId, String correlationId) {
        DocumentEntity entity = requireActive(documentId);
        UserContext actor = UserContextHolder.require();
        checkReadAccess(entity.getDocumentType(), actor);
        audit(documentId, entity.getClaimId(), "DOWNLOADED", actor, correlationId, null);
        return storagePort.generateDownloadUrl(entity.getStorageKey());
    }

    @Transactional(readOnly = true)
    public Resource loadAsResource(String storageKey, String correlationId) {
        return resolveFileByStorageKey(storageKey).resource();
    }

    @Transactional(readOnly = true)
    public DocumentFileStream streamDocumentById(UUID documentId, String correlationId) {
        DocumentEntity entity = requireActive(documentId);
        UserContext actor = UserContextHolder.require();
        checkReadAccess(entity.getDocumentType(), actor);
        audit(documentId, entity.getClaimId(), "VIEWED", actor, correlationId, null);
        DocumentFileStream stream = resolveFileByStorageKey(entity.getStorageKey());
        String contentType = entity.getContentType() != null && !entity.getContentType().isBlank()
                ? entity.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return new DocumentFileStream(stream.resource(), contentType, entity.getFilename());
    }

    // ─── Soft Delete ─────────────────────────────────────────────────────────

    /**
     * Marks a document ARCHIVED instead of physically deleting it.
     * Physical deletion is only permitted for GDPR right-to-erasure workflows
     * via a dedicated compliance job — never on user request.
     */
    @Transactional
    public void deleteDocument(UUID documentId, String requestingUserId, String correlationId) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
        doc.setStatus(DocumentStatus.ARCHIVED);
        documentRepository.save(doc);
        UserContext actor = UserContextHolder.require();
        audit(documentId, doc.getClaimId(), "ARCHIVED", actor, correlationId, null);
        log.info("[{}] Document {} archived (soft-deleted) by user {}", correlationId, documentId, requestingUserId);
    }

    // ─── Audit history ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentAuditLogEntity> getAuditHistory(UUID documentId, String correlationId) {
        if (!documentRepository.existsById(documentId)) {
            throw new NotFoundException("Document", documentId.toString());
        }
        UserContext actor = UserContextHolder.require();
        if (!actor.isAuditor() && !actor.isCaseManager()) {
            throw new DomainException("DOC_FORBIDDEN", "Only auditors and case managers may view audit history.");
        }
        return auditLogRepository.findByDocumentIdOrderByOccurredAtDesc(documentId);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private DocumentEntity requireActive(UUID documentId) {
        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document", documentId.toString()));
        if (entity.getStatus() == DocumentStatus.ARCHIVED) {
            throw new DomainException("DOC_ARCHIVED", "This document has been archived and is no longer accessible.");
        }
        return entity;
    }

    private void checkReadAccess(DocumentType type, UserContext actor) {
        if (actor.isAuditor() || actor.isCaseManager()) return;
        if (actor.isCustomer() && CUSTOMER_RESTRICTED.contains(type)) {
            throw new DomainException("DOC_FORBIDDEN", "You are not authorised to access this document type.");
        }
    }

    private void checkWriteAccess(DocumentType type, UserContext actor) {
        if (actor.isAuditor() || actor.isCaseManager()) return;
        Set<String> allowed = WRITE_ROLES.get(type);
        if (allowed == null) return; // no restriction defined — any authenticated role may upload
        boolean permitted = actor.roles().stream().anyMatch(allowed::contains);
        if (!permitted) {
            throw new DomainException("DOC_FORBIDDEN",
                    "Your role is not permitted to upload documents of type " + type);
        }
    }

    private boolean canRead(DocumentType type, UserContext actor) {
        if (actor.isAuditor() || actor.isCaseManager()) return true;
        if (actor.isCustomer()) return !CUSTOMER_RESTRICTED.contains(type);
        return true;
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
        long existingCount = documentRepository.countByClaimIdAndStatus(claimId, DocumentStatus.ACTIVE);
        if (existingCount >= storageProperties.getMaxDocumentsPerClaim()) {
            throw new DomainException("DOC_LIMIT_REACHED",
                    "Maximum of " + storageProperties.getMaxDocumentsPerClaim() +
                    " documents per claim has been reached.");
        }
        log.debug("[{}] File validation passed: {} ({} bytes, {})",
                correlationId, file.getOriginalFilename(), file.getSize(), contentType);
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

    private void audit(UUID documentId, UUID claimId, String action,
                       UserContext actor, String correlationId, String metadata) {
        DocumentAuditLogEntity log = new DocumentAuditLogEntity();
        log.setId(UUID.randomUUID());
        log.setDocumentId(documentId);
        log.setClaimId(claimId);
        log.setAction(action);
        log.setActorUserId(actor.userId());
        log.setActorRole(actor.roles().isEmpty() ? null : actor.roles().getFirst());
        log.setCorrelationId(correlationId);
        log.setOccurredAt(Instant.now());
        log.setMetadata(metadata != null ? "{\"info\":\"" + metadata + "\"}" : null);
        auditLogRepository.save(log);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            log.warn("SHA-256 checksum computation failed", e);
            return null;
        }
    }

    private DocumentType parseType(String raw) {
        try {
            return DocumentType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("DOC_INVALID_TYPE", "Unknown document type: " + raw);
        }
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
                .version(entity.getVersion())
                .status(entity.getStatus().name())
                .checksumSha256(entity.getChecksumSha256())
                .build();
    }
}
