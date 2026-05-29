package com.amazondemo.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * AWS S3 configuration for product image uploads.
 *
 * Supports multiple environments:
 *   - local/test: AWS disabled, files stored locally
 *   - stage:      LocalStack at http://localstack:4566 (path-style access)
 *   - prod:       Real AWS S3 (no endpoint override, no path-style)
 *
 * Activated only when app.aws.enabled=true (stage and prod profiles).
 */
@Configuration
@Slf4j
public class AwsConfig {

    @Value("${app.aws.region:us-east-1}")
    private String region;

    @Value("${app.aws.access-key:test}")
    private String accessKey;

    @Value("${app.aws.secret-key:test}")
    private String secretKey;

    @Value("${app.aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${app.aws.s3.path-style-access:false}")
    private boolean pathStyleAccess;

    @Value("${app.aws.enabled:false}")
    private boolean awsEnabled;

    /**
     * S3 client - created when aws.enabled=true.
     * In staging: points to LocalStack endpoint.
     * In prod: uses real AWS SDK defaults (endpoint from DNS).
     */
    @Bean
    @ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
    public S3Client s3Client() {
        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(c -> c.pathStyleAccessEnabled(pathStyleAccess));

        if (s3Endpoint != null && !s3Endpoint.isBlank()) {
            builder.endpointOverride(URI.create(s3Endpoint));
            log.info("S3 client configured with custom endpoint: {}", s3Endpoint);
        } else {
            log.info("S3 client configured for real AWS region: {}", region);
        }

        return builder.build();
    }

    /**
     * S3 Presigner for generating pre-signed upload/download URLs.
     */
    @Bean
    @ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
    public S3Presigner s3Presigner() {
        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

        if (s3Endpoint != null && !s3Endpoint.isBlank()) {
            builder.endpointOverride(URI.create(s3Endpoint));
        }

        return builder.build();
    }
}
