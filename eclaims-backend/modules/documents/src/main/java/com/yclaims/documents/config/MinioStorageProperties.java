package com.yclaims.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO / S3-compatible object storage configuration.
 * Active when Spring profile = "minio".
 *
 * For local dev: points at the MinIO container in docker-compose (localhost:9000).
 * For production: point at real AWS S3 by changing endpoint to the S3 regional endpoint
 *   and supplying real AWS credentials — zero code change required.
 */
@Component
@ConfigurationProperties(prefix = "eclaims.minio")
public class MinioStorageProperties {

    private String endpoint    = "http://localhost:9000";
    private String accessKey   = "eclaims";
    private String secretKey   = "eclaims_dev";
    private String bucketName  = "eclaims-documents";

    /** Pre-signed URL expiry in minutes (browser downloads direct from MinIO/S3). */
    private int presignedUrlExpiryMinutes = 60;

    public String getEndpoint()                  { return endpoint; }
    public void   setEndpoint(String v)          { this.endpoint = v; }

    public String getAccessKey()                 { return accessKey; }
    public void   setAccessKey(String v)         { this.accessKey = v; }

    public String getSecretKey()                 { return secretKey; }
    public void   setSecretKey(String v)         { this.secretKey = v; }

    public String getBucketName()                { return bucketName; }
    public void   setBucketName(String v)        { this.bucketName = v; }

    public int    getPresignedUrlExpiryMinutes() { return presignedUrlExpiryMinutes; }
    public void   setPresignedUrlExpiryMinutes(int v) { this.presignedUrlExpiryMinutes = v; }
}
