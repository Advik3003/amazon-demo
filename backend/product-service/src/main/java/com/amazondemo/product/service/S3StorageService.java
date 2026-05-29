package com.amazondemo.product.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * S3 storage service for product images.
 * Available only when app.aws.enabled=true (stage and prod environments).
 *
 * In local environment, falls back to returning a placeholder URL.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
public class S3StorageService {

    @Value("${app.aws.s3.bucket-name:amazon-demo-stage}")
    private String bucketName;

    @Value("${app.aws.s3.product-images-prefix:product-images/}")
    private String productImagesPrefix;

    @Value("${app.aws.s3.cdn-url:}")
    private String cdnUrl;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Autowired
    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /**
     * Upload a product image to S3.
     * Returns the S3 object key (path) for the uploaded file.
     */
    public String uploadProductImage(MultipartFile file, String productId) throws IOException {
        String fileName = productImagesPrefix + productId + "/" + UUID.randomUUID() + getExtension(file.getOriginalFilename());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        log.info("Uploaded product image: {} to bucket: {}", fileName, bucketName);

        return fileName;
    }

    /**
     * Get the public URL for a product image.
     * If a CDN URL is configured, uses that; otherwise constructs S3 URL.
     */
    public String getProductImageUrl(String s3Key) {
        if (cdnUrl != null && !cdnUrl.isBlank()) {
            return cdnUrl + "/" + s3Key;
        }
        return generatePresignedUrl(s3Key, 3600); // 1 hour expiry
    }

    /**
     * Generate a pre-signed URL for temporary access.
     */
    public String generatePresignedUrl(String s3Key, int expirySeconds) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(r -> r.bucket(bucketName).key(s3Key))
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Delete a product image from S3.
     */
    public void deleteProductImage(String s3Key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            s3Client.deleteObject(request);
            log.info("Deleted product image: {} from bucket: {}", s3Key, bucketName);
        } catch (Exception e) {
            log.warn("Failed to delete product image: {} - {}", s3Key, e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
