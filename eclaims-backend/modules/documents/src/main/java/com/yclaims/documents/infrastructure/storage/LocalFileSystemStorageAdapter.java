package com.yclaims.documents.infrastructure.storage;

import com.yclaims.documents.config.StorageProperties;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * POC document storage: local filesystem.
 * Documents stored in: ${eclaims.storage.path} (default: ./uploads/)
 *
 * Production replacement: S3DocumentStorageAdapter behind the same DocumentStoragePort.
 * Extraction requires zero domain/application code change — only the adapter swaps.
 */
@Component
@Profile({"local", "test", "default"})
@Slf4j
public class LocalFileSystemStorageAdapter implements DocumentStoragePort {

    private final Path storageRoot;
    private final String baseDownloadUrl;

    public LocalFileSystemStorageAdapter(
            StorageProperties storageProperties,
            @Value("${eclaims.api.base-url:http://localhost:8090}") String baseUrl) {
        this.storageRoot = Paths.get(storageProperties.getPath());
        this.baseDownloadUrl = baseUrl + "/api/v1/documents/download/";
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create document storage directory: " + this.storageRoot, e);
        }
    }

    @Override
    public String store(UUID documentId, String filename, String contentType,
                        InputStream content, long contentLength) {
        String storageKey = documentId.toString() + "_" + sanitize(filename);
        Path target = storageRoot.resolve(storageKey);

        try {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored document {} ({} bytes) at {}", documentId, contentLength, target);
            return storageKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store document " + documentId, e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        return baseDownloadUrl + storageKey;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(storageRoot.resolve(storageKey));
            log.info("Deleted document at key {}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete document at key {}: {}", storageKey, e.getMessage());
        }
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
