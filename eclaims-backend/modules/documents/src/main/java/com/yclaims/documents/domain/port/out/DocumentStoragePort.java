package com.yclaims.documents.domain.port.out;

import java.io.InputStream;
import java.util.UUID;

/**
 * Port for document binary storage.
 * POC implementation: LocalFileSystemStorageAdapter (stores in ./uploads/)
 * Production implementation: S3DocumentStorageAdapter (AWS S3 with pre-signed URLs)
 *
 * The domain and application layers have zero knowledge of S3 or filesystem —
 * they only know this interface.
 */
public interface DocumentStoragePort {

    /**
     * Store document bytes. Returns the storage key/path for later retrieval.
     */
    String store(UUID documentId, String filename, String contentType, InputStream content, long contentLength);

    /**
     * Generate a pre-signed URL for direct browser download (bypasses API — no blocking).
     * For local FS: returns a direct download URL via the API.
     * For S3: returns a 1-hour pre-signed URL.
     */
    String generateDownloadUrl(String storageKey);

    /**
     * Delete document from storage (GDPR compliance).
     */
    void delete(String storageKey);
}
