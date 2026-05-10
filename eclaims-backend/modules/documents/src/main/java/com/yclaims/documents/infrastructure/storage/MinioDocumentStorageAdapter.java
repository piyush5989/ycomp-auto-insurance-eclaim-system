package com.yclaims.documents.infrastructure.storage;

import com.yclaims.documents.config.MinioStorageProperties;
import com.yclaims.documents.domain.port.out.DocumentStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade document storage: MinIO (S3-compatible object store).
 * Active when Spring profile = "minio".
 *
 * For production on AWS: swap {@code endpoint} to the S3 regional endpoint
 *   (e.g. https://s3.us-east-1.amazonaws.com) and supply real IAM credentials —
 *   zero code change required; only configuration changes.
 *
 * Switching from local filesystem to this adapter:
 *   set environment variable SPRING_PROFILES_ACTIVE=minio
 */
@Component
@Profile("minio")
@Slf4j
public class MinioDocumentStorageAdapter implements DocumentStoragePort {

    private final MinioClient minioClient;
    private final String bucketName;
    private final int presignedUrlExpiryMinutes;

    public MinioDocumentStorageAdapter(MinioStorageProperties props) {
        this.bucketName = props.getBucketName();
        this.presignedUrlExpiryMinutes = props.getPresignedUrlExpiryMinutes();
        this.minioClient = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }

    /**
     * Ensures the bucket exists on startup — creates it if not.
     * In a real AWS environment the bucket is pre-created via IaC (Terraform/CDK);
     * this auto-creation is convenient for local dev and demo.
     */
    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] Created bucket '{}'", bucketName);
            } else {
                log.info("[MinIO] Bucket '{}' ready", bucketName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO bucket initialisation failed for '" + bucketName + "'", e);
        }
    }

    /**
     * Uploads the document stream to MinIO.
     * Storage key format: {@code {claimId}/{documentId}_{sanitized_filename}}
     * This gives a logical per-claim folder structure inside the bucket.
     */
    @Override
    public String store(UUID documentId, String filename, String contentType,
                        InputStream content, long contentLength) {
        String objectKey = documentId + "/" + sanitize(filename);
        String resolvedContentType = contentType != null ? contentType : "application/octet-stream";
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(content, contentLength, -1)
                            .contentType(resolvedContentType)
                            .build());
            log.info("[MinIO] Stored object '{}' ({} bytes) in bucket '{}'",
                    objectKey, contentLength, bucketName);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store document '" + objectKey + "' in MinIO", e);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "MinIO object " + bucketName + "/" + storageKey;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                try {
                    return minioClient.getObject(
                            GetObjectArgs.builder()
                                    .bucket(bucketName)
                                    .object(storageKey)
                                    .build());
                } catch (Exception e) {
                    throw new IOException("Failed to open MinIO object stream for key: " + storageKey, e);
                }
            }
        };
    }

    /**
     * Generates a pre-signed GET URL valid for {@code presignedUrlExpiryMinutes}.
     * The client downloads directly from MinIO — the API server carries zero file-transfer load.
     */
    @Override
    public String generateDownloadUrl(String storageKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry(presignedUrlExpiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed URL for '" + storageKey + "'", e);
        }
    }

    /**
     * Removes the object from MinIO.
     * Note: in a compliance scenario this is called only by the GDPR/retention service,
     * never on a user delete request (which uses soft-delete in the DB).
     */
    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build());
            log.info("[MinIO] Deleted object '{}' from bucket '{}'", storageKey, bucketName);
        } catch (Exception e) {
            log.warn("[MinIO] Failed to delete object '{}': {}", storageKey, e.getMessage());
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "document";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
