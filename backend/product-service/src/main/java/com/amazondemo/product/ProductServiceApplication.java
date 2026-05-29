package com.amazondemo.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Product Service Application
 * ============================
 * Implements the CQRS (Command Query Responsibility Segregation) pattern:
 *
 * COMMAND SIDE (Write):
 * - Creates/Updates/Deletes products in PostgreSQL
 * - On every write, publishes an event to Kafka
 *
 * QUERY SIDE (Read):
 * - Kafka consumer receives events and writes to MongoDB
 * - All GET requests read from MongoDB (optimized for read)
 * - Redis caches hot products
 *
 * WHY CQRS?
 * - Read-heavy system (1000x more reads than writes for products)
 * - MongoDB is optimized for flexible, denormalized reads
 * - PostgreSQL is optimized for ACID writes
 * - Can scale read and write sides independently
 *
 * DATA FLOW:
 * POST /products -> ProductCommandService -> PostgreSQL + Kafka event
 * Kafka consumer -> MongoDB (read model)
 * GET /products -> ProductQueryService -> Redis cache -> MongoDB
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableJpaAuditing
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
