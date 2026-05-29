package com.amazondemo.notification.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Notification stored in MongoDB
 * MongoDB is perfect here because:
 * - Notifications have flexible structure (order vs payment vs promo)
 * - High volume of reads per user
 * - Easy to query by userId + read/unread status
 */
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    private String userId;
    private String title;
    private String message;

    @Builder.Default
    private String type = "INFO";  // ORDER, PAYMENT, PROMO, SYSTEM

    private String referenceId;    // orderId, paymentId, etc.
    private String referenceType;  // ORDER, PAYMENT

    @Builder.Default
    private boolean read = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
