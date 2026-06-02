package com.amazondemo.payment.service;

import com.amazondemo.common.event.PaymentEvent;
import com.amazondemo.payment.dto.PaymentRequest;
import com.amazondemo.payment.dto.PaymentResponse;
import com.amazondemo.payment.model.Payment;
import com.amazondemo.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment Service - Dummy Implementation
 * ========================================
 * Simulates payment processing.
 *
 * In a real app, this would integrate with:
 * - Stripe: stripe.com/docs
 * - PayPal: developer.paypal.com
 * - Razorpay (India): razorpay.com/docs
 *
 * HOW DUMMY PAYMENT WORKS:
 * - 90% of payments succeed (configurable success rate)
 * - 10% fail randomly (to demonstrate error handling)
 * - 1 second delay (to simulate real processing)
 *
 * AFTER PAYMENT:
 * - Publishes PAYMENT_SUCCESS or PAYMENT_FAILED event to Kafka
 * - Order service receives event and updates order status
 * - Notification service sends email to customer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.payment.success-rate:0.9}")
    private double successRate;

    @Value("${app.payment.processing-delay-ms:1000}")
    private long processingDelayMs;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        log.info("Processing payment for order: {} amount: {}", request.getOrderId(), request.getAmount());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing payment for idempotency key: {}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .paymentMethod(Payment.PaymentMethod.valueOf(
                        request.getPaymentMethod() != null ? request.getPaymentMethod() : "CREDIT_CARD"))
                .status(Payment.PaymentStatus.PROCESSING)
                .build();

        Payment saved = Optional.ofNullable(paymentRepository.save(payment))
                .orElseThrow(() -> new IllegalStateException("Failed to persist payment"));

        // Simulate processing delay
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate success/failure based on success rate
        boolean isSuccess = Math.random() < successRate;

        if (isSuccess) {
            saved.setStatus(Payment.PaymentStatus.SUCCESS);
            saved.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            log.info("Payment SUCCESS for order: {} transaction: {}", request.getOrderId(), saved.getTransactionId());
        } else {
            saved.setStatus(Payment.PaymentStatus.FAILED);
            saved.setFailureReason("Payment declined by bank (simulated)");
            log.warn("Payment FAILED for order: {}", request.getOrderId());
        }

        Payment finalPayment = Optional.ofNullable(paymentRepository.save(saved))
                .orElseThrow(() -> new IllegalStateException("Failed to update payment state"));

        // Publish payment event to Kafka
        publishPaymentEvent(finalPayment, isSuccess ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED");

        return toResponse(finalPayment);
    }

    public PaymentResponse getPaymentByOrder(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::toResponse)
                .orElse(null);
    }

    private void publishPaymentEvent(Payment payment, String eventType) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod().name())
                .transactionId(payment.getTransactionId())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .eventTime(LocalDateTime.now())
                .build();

        kafkaTemplate.send("payment-events", Objects.requireNonNull(payment.getOrderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment event", ex);
                    }
                });
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod().name())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
