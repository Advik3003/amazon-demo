package com.amazondemo.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Order Service Application
 * ==========================
 * Manages the complete order lifecycle:
 * PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
 * CANCELLED (can happen from PENDING or CONFIRMED)
 *
 * KEY INTEGRATIONS:
 * - Feign Client -> Inventory Service (check stock, reserve items)
 * - Feign Client -> Payment Service (process payment)
 * - Kafka -> Publishes order events for other services
 * - RabbitMQ -> Payment notifications from payment service
 *
 * CQRS IMPLEMENTATION:
 * - Write model: PostgreSQL (order_db)
 * - Read model: MongoDB (order_read_db) - populated by Kafka events
 * - Redis: Caches order status for fast polling
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaAuditing
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
