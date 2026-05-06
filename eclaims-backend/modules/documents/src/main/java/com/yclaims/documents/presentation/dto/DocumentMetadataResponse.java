package com.yclaims.documents.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class DocumentMetadataResponse {
    private UUID documentId;
    private UUID claimId;
    private String documentType;
    private String filename;
    private String contentType;
    private long fileSizeBytes;
    private String downloadUrl;
    private String uploadedByUserId;
    private Instant uploadedAt;
    private int version;
    private String status;
    private String checksumSha256;
}
