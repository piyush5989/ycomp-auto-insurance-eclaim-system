package com.yclaims.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "eclaims.storage")
public class StorageProperties {

    private long maxFileSizeBytes = 5_242_880L; // 5 MB default
    private int maxDocumentsPerClaim = 10;
    private List<String> allowedContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getMaxDocumentsPerClaim() { return maxDocumentsPerClaim; }
    public void setMaxDocumentsPerClaim(int maxDocumentsPerClaim) { this.maxDocumentsPerClaim = maxDocumentsPerClaim; }

    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> allowedContentTypes) { this.allowedContentTypes = allowedContentTypes; }
}
