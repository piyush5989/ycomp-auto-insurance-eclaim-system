package com.yclaims.documents.presentation;

import com.yclaims.documents.application.DocumentApplicationService;
import com.yclaims.documents.infrastructure.persistence.DocumentAuditLogEntity;
import com.yclaims.documents.presentation.dto.DocumentMetadataResponse;
import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Document upload and retrieval API.
 * Document binaries bypass the API via pre-signed URLs (async — user never waits for S3 upload).
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document upload and management")
public class DocumentController {

    private final DocumentApplicationService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@authz.isAllowed('document', 'upload')")
    @Operation(summary = "Upload a document for a claim")
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocument(
            @RequestParam UUID claimId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) throws Exception {

        DocumentMetadataResponse response = documentService.uploadDocument(
                claimId, documentType, file, UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("@authz.isAllowed('document', 'list')")
    @Operation(summary = "List all documents for a claim")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponse>>> listDocuments(
            @PathVariable UUID claimId) {
        List<DocumentMetadataResponse> docs = documentService.listDocumentsByClaimId(claimId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(docs, correlationId()));
    }

    @GetMapping("/{documentId}/download-url")
    @PreAuthorize("@authz.isAllowed('document', 'download')")
    @Operation(summary = "Get a direct storage URL (legacy). Prefer GET /{documentId}/file with Authorization — browser tabs cannot send JWT.")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(@PathVariable UUID documentId) {
        String url = documentService.getDownloadUrl(documentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(url, correlationId()));
    }

    /**
     * Authenticated binary stream — SPAs should call this with Axios/fetch (Bearer token).
     * Avoids broken downloads when users open {@code /download/{storageKey}} in a new tab (no JWT).
     */
    @GetMapping("/{documentId}/file")
    @PreAuthorize("@authz.isAllowed('document', 'download')")
    @Operation(summary = "Download or view document bytes (send Authorization: Bearer)")
    public ResponseEntity<Resource> streamDocumentById(@PathVariable UUID documentId) {
        var stream = documentService.streamDocumentById(documentId, correlationId());
        String safeName = stream.filename() != null ? stream.filename() : "document";
        boolean inline = stream.contentType() != null
                && (stream.contentType().startsWith("image/") || stream.contentType().startsWith("video/"));
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(safeName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(stream.contentType()))
                .body(stream.resource());
    }

    @GetMapping("/download/{storageKey:.+}")
    @PreAuthorize("@authz.isAllowed('document', 'download')")
    @Operation(summary = "Download by storage key (API clients with Bearer only — not for raw browser navigation)")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String storageKey) {
        Resource resource = documentService.loadAsResource(storageKey, correlationId());
        String filename = storageKey.contains("/") ? storageKey.substring(storageKey.lastIndexOf('/') + 1) : storageKey;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("@authz.isAllowed('document', 'delete')")
    @Operation(summary = "Archive a document (soft delete — binary preserved for compliance)")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId, UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(null, correlationId()));
    }

    @GetMapping("/{documentId}/audit")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CASE_MANAGER')")
    @Operation(summary = "Retrieve the full audit trail for a document (auditors and case managers only)")
    public ResponseEntity<ApiResponse<List<DocumentAuditLogEntity>>> getAuditHistory(
            @PathVariable UUID documentId) {
        List<DocumentAuditLogEntity> history = documentService.getAuditHistory(documentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(history, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
