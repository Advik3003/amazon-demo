package com.amazondemo.notification.consumer;

import com.amazondemo.common.event.OrderEvent;
import com.amazondemo.common.event.PaymentEvent;
import com.amazondemo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Event Consumer for Notifications
 * ==================================
 * Listens to order and payment events and sends appropriate notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;

    /**
     * Listen to order events via Kafka
     */
    @KafkaListener(topics = "order-events", groupId = "notification-service-consumer")
    public void handleOrderEvent(OrderEvent event) {
        log.info("Notification service received order event: {} for order: {}",
                event.getEventType(), event.getOrderId());

        String title;
        String message;

        switch (event.getEventType()) {
            case "ORDER_PLACED" -> {
                title = "Order Placed Successfully!";
                message = String.format("Your order #%s has been placed. Total: $%.2f",
                        event.getOrderId(), event.getTotalAmount());
                notificationService.sendEmail(event.getUserEmail(), title,
                        message + "\n\nThank you for shopping at Amazon Demo!");
            }
            case "ORDER_CONFIRMED" -> {
                title = "Order Confirmed";
                message = "Your order has been confirmed and is being processed.";
            }
            case "ORDER_SHIPPED" -> {
                title = "Your Order Has Shipped!";
                message = "Your order is on the way!";
                notificationService.sendEmail(event.getUserEmail(), title, message);
            }
            case "ORDER_DELIVERED" -> {
                title = "Order Delivered";
                message = "Your order has been delivered. Enjoy!";
                notificationService.sendEmail(event.getUserEmail(), title, message);
            }
            case "ORDER_CANCELLED" -> {
                title = "Order Cancelled";
                message = "Your order has been cancelled.";
            }
            default -> {
                title = "Order Update";
                message = "Your order status has been updated.";
            }
        }

        notificationService.createNotification(
                event.getUserId(), title, message,
                "ORDER", event.getOrderId(), "ORDER");
    }

    /**
     * Listen to payment events via Kafka
     * (Could also be via RabbitMQ - demonstrates both)
     */
    @KafkaListener(topics = "payment-events", groupId = "notification-service-payment")
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Notification service received payment event: {}", event.getEventType());

        String title;
        String message;

        switch (event.getEventType()) {
            case "PAYMENT_SUCCESS" -> {
                title = "Payment Successful!";
                message = String.format("Payment of $%.2f received. Transaction ID: %s",
                        event.getAmount(), event.getTransactionId());
            }
            case "PAYMENT_FAILED" -> {
                title = "Payment Failed";
                message = "Your payment could not be processed. Please try again or use a different payment method.";
            }
            default -> {
                title = "Payment Update";
                message = "Your payment status has been updated.";
            }
        }

        notificationService.createNotification(
                event.getUserId(), title, message,
                "PAYMENT", event.getPaymentId(), "PAYMENT");
    }
}
