package com.yclaims.documents.infrastructure.storage;

import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * S3-compatible document storage using MinIO.
 * Active on profiles: dev, prod.
 *
 * Local dev: MinIO container in docker-compose (localhost:9000, console on 9001).
 * Production: swap endpoint + credentials to AWS S3 via ECLAIMS_MINIO_* env vars.
 *
 * Pre-signed URL expiry: 1 hour — browser downloads go directly to MinIO/S3,
 * never through the API server (no blocking, no memory overhead).
 *
 * The domain and application layers have zero MinIO knowledge —
 * they only know DocumentStoragePort. Swapping back to local FS (or to GCS)
 * requires zero domain/application code changes.
 */
@Component
@Profile({"dev", "prod"})
@Slf4j
public class MinioDocumentStorageAdapter implements DocumentStoragePort {

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioDocumentStorageAdapter(
            @Value("${eclaims.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${eclaims.minio.access-key:eclaims}") String accessKey,
            @Value("${eclaims.minio.secret-key:eclaims_secret}") String secretKey,
            @Value("${eclaims.minio.bucket:eclaims-documents}") String bucketName) {

        this.bucketName = bucketName;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucketExists();
    }

    @Override
    public String store(UUID documentId, String filename, String contentType,
                        InputStream content, long contentLength) {
        String objectKey = documentId + "/" + sanitize(filename);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(content, contentLength, -1)
                    .contentType(contentType)
                    .build());
            log.info("MinIO: stored document {} as {}/{}", documentId, bucketName, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store document " + documentId + " in MinIO", e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(storageKey)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed URL for " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .build());
            log.info("MinIO: deleted object {}/{}", bucketName, storageKey);
        } catch (Exception e) {
            log.warn("MinIO: failed to delete {}: {}", storageKey, e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO: created bucket '{}'", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO: could not verify/create bucket '{}': {} — " +
                    "ensure MinIO is running before starting the API", bucketName, e.getMessage());
        }
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
