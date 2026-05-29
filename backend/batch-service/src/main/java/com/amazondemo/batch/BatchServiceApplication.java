package com.amazondemo.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Batch Service Application
 * ==========================
 * Spring Batch implementation for background processing:
 *
 * 1. ORDER REPORT JOB (Daily at 1 AM):
 *    - Read all orders from last 24 hours
 *    - Calculate metrics (total sales, avg order value, etc.)
 *    - Write to reports table
 *
 * 2. INVENTORY SYNC JOB (Every 30 minutes):
 *    - Read inventory from product-service
 *    - Sync with local inventory records
 *    - Alert on low stock
 *
 * 3. CLEANUP JOB (Daily at 2 AM):
 *    - Delete old expired tokens
 *    - Archive old notifications
 *    - Clean up temporary data
 *
 * SPRING BATCH CONCEPTS:
 * - Job: A complete batch process (e.g., order-report-job)
 * - Step: A phase of a job (read -> process -> write)
 * - ItemReader: Reads data (database, file, API)
 * - ItemProcessor: Transforms data
 * - ItemWriter: Writes output (database, file, email)
 * - Chunk: Processes N items at a time (commit-interval)
 * - JobRepository: Tracks job execution history
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class BatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
