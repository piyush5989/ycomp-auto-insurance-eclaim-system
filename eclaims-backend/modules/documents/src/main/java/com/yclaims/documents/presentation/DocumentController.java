package com.yclaims.documents.presentation;

import com.yclaims.documents.application.DocumentApplicationService;
import com.yclaims.documents.presentation.dto.DocumentMetadataResponse;
import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR')")
    @Operation(summary = "Upload a document for a claim")
    public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocument(
            @RequestParam UUID claimId,
            @RequestParam String documentType,
            @RequestPart MultipartFile file) throws Exception {

        DocumentMetadataResponse response = documentService.uploadDocument(
                claimId, documentType, file, UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/claim/{claimId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "List all documents for a claim")
    public ResponseEntity<ApiResponse<List<DocumentMetadataResponse>>> listDocuments(
            @PathVariable UUID claimId) {
        List<DocumentMetadataResponse> docs = documentService.listDocumentsByClaimId(claimId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(docs, correlationId()));
    }

    @GetMapping("/{documentId}/download-url")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "Get a pre-signed download URL for a document")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(@PathVariable UUID documentId) {
        String url = documentService.getDownloadUrl(documentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(url, correlationId()));
    }

    @GetMapping("/download/{storageKey:.+}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "Download a document by storage key")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String storageKey) {
        Resource resource = documentService.loadAsResource(storageKey, correlationId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR')")
    @Operation(summary = "Delete a document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId, UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(null, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
