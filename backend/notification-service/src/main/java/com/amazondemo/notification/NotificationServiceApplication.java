package com.amazondemo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Notification Service
 * =====================
 * Listens to events from Kafka and RabbitMQ and sends notifications.
 *
 * WHY BOTH KAFKA AND RABBITMQ?
 * - Kafka: Consumes ORDER events (order placed, shipped, delivered)
 * - RabbitMQ: Consumes PAYMENT events (payment success, failure)
 *
 * This demonstrates using both messaging systems in the same application.
 * In practice, you'd typically use one. This is for learning purposes.
 *
 * NOTIFICATION TYPES:
 * - Email: Order confirmation, shipping updates, payment receipts
 * - In-app: Real-time notifications stored in MongoDB
 *
 * In production, email would use:
 * - Amazon SES (AWS)
 * - SendGrid
 * - Mailchimp
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
