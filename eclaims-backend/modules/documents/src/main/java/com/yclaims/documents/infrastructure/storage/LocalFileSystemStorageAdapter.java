package com.yclaims.documents.infrastructure.storage;

import com.yclaims.documents.config.StorageProperties;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Dev/test document storage: local filesystem.
 *
 * Active whenever the Spring profile {@code minio} is NOT in the active set.
 * The {@code !minio} expression is used (rather than naming "local"/"test"/
 * "default" explicitly) so that the adapter selection is decided on the
 * single MinIO switch, regardless of any other profiles that may be active
 * at the same time. The Spring Boot Maven plugin's {@code spring-boot.run.profiles}
 * argument *adds* to the existing active set rather than replacing it, which
 * would otherwise activate this adapter alongside {@link MinioDocumentStorageAdapter}
 * and break the {@link DocumentStoragePort} injection with a "found 2 beans" error.
 *
 * Documents stored in: ${eclaims.storage.path} (default: ./uploads/)
 *
 * To switch to MinIO (S3-compatible object store):
 *   set SPRING_PROFILES_ACTIVE=minio (in .env or as an environment variable),
 *   or run scripts/restart-backend.ps1 -Profile minio. MinIO runs on
 *   localhost:9000, console on localhost:9001.
 *
 * For production AWS S3: same MinioDocumentStorageAdapter, different endpoint
 * + IAM credentials. Zero domain/application code change needed - only the
 * Spring profile and config change.
 */
@Component
@Profile("!minio")
@Slf4j
public class LocalFileSystemStorageAdapter implements DocumentStoragePort {

    private final Path storageRoot;
    private final String baseDownloadUrl;

    public LocalFileSystemStorageAdapter(
            StorageProperties storageProperties,
            @Value("${eclaims.api.base-url:http://localhost:8090}") String baseUrl) {
        // Resolve to an absolute, normalised path up front. The configured
        // value may be relative (e.g. "./uploads"), but the safety check in
        // loadAsResource compares against this root after Path.normalize() has
        // already stripped any "." segments from the resolved file path. If
        // we leave the root in its original relative form, a legitimate file
        // resolves to a normalised path that does not start with the still-
        // unnormalised root and the guard incorrectly rejects it as a path
        // traversal (yielding a 500 on every view/download).
        this.storageRoot = Paths.get(storageProperties.getPath()).toAbsolutePath().normalize();
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
    public Resource loadAsResource(String storageKey) {
        Path file = storageRoot.resolve(storageKey).normalize();
        if (!file.startsWith(storageRoot)) {
            throw new RuntimeException("Resolved path escapes storage root for key: " + storageKey);
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) return resource;
            throw new RuntimeException("Document not found for key: " + storageKey);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid document URL for key: " + storageKey, e);
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
