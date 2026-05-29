package com.amazondemo.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * AWS SES and SQS configuration for notification service.
 *
 * Supports multiple environments:
 *   - local/test: AWS disabled, uses Mailhog/RabbitMQ
 *   - stage:      LocalStack SES + SQS at http://localstack:4566
 *   - prod:       Real AWS SES + SQS
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

    @Value("${app.aws.ses.endpoint:}")
    private String sesEndpoint;

    @Value("${app.aws.sqs.endpoint:}")
    private String sqsEndpoint;

    /**
     * SES client for sending emails.
     * Stage: LocalStack endpoint. Prod: Real AWS SES.
     */
    @Bean
    @ConditionalOnProperty(name = "app.aws.ses.enabled", havingValue = "true")
    public SesClient sesClient() {
        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var builder = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

        if (sesEndpoint != null && !sesEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sesEndpoint));
            log.info("SES client configured with custom endpoint: {}", sesEndpoint);
        } else {
            log.info("SES client configured for real AWS region: {}", region);
        }

        return builder.build();
    }

    /**
     * SQS client for consuming order/payment notification queues.
     * Stage: LocalStack endpoint. Prod: Real AWS SQS.
     */
    @Bean
    @ConditionalOnProperty(name = "app.features.sqs-events", havingValue = "true")
    public SqsClient sqsClient() {
        var credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        var builder = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
            log.info("SQS client configured with custom endpoint: {}", sqsEndpoint);
        } else {
            log.info("SQS client configured for real AWS region: {}", region);
        }

        return builder.build();
    }
}
