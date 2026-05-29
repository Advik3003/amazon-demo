package com.amazondemo.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring Batch Configuration
 * ============================
 * Defines jobs and their steps.
 *
 * STEP ANATOMY:
 * Step = ItemReader + ItemProcessor + ItemWriter + chunk-size
 *
 * CHUNK PROCESSING:
 * - Read 10 items -> Process 10 items -> Write 10 items -> COMMIT
 * - If any step fails, only the current chunk is rolled back
 * - Efficient for large datasets
 *
 * WHY SPRING BATCH?
 * - Built-in retry/skip for failed records
 * - Tracks job execution history
 * - Restartable (failed jobs can resume from where they left off)
 * - Parallel processing support
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // ==================== ORDER REPORT JOB ====================

    @Bean
    public Job orderReportJob() {
        return new JobBuilder("orderReportJob", jobRepository)
                .start(orderReportStep())
                .build();
    }

    @Bean
    public Step orderReportStep() {
        return new StepBuilder("orderReportStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(10, transactionManager)
                .reader(orderReportReader())
                .processor(orderReportProcessor())
                .writer(orderReportWriter())
                .faultTolerant()
                .retryLimit(3)                           // Retry failed items up to 3 times
                .retry(Exception.class)                  // Which exceptions to retry
                .skipLimit(5)                            // Skip up to 5 failing items
                .skip(Exception.class)                   // Which exceptions to skip
                .build();
    }

    @Bean
    public ItemReader<Map<String, Object>> orderReportReader() {
        // In production, this would read from the order database
        log.info("Batch: Reading orders for report generation");
        List<Map<String, Object>> dummyOrders = new ArrayList<>();
        // Dummy data - in production, use JdbcCursorItemReader or JpaPagingItemReader
        dummyOrders.add(Map.of("orderId", "ORD-001", "amount", 99.99, "status", "DELIVERED"));
        dummyOrders.add(Map.of("orderId", "ORD-002", "amount", 149.99, "status", "DELIVERED"));
        return new ListItemReader<>(dummyOrders);
    }

    @Bean
    public ItemProcessor<Map<String, Object>, Map<String, Object>> orderReportProcessor() {
        return item -> {
            // Transform/enrich the data
            log.debug("Processing order for report: {}", item.get("orderId"));
            item = new java.util.HashMap<>(item);
            ((java.util.Map<String, Object>) item).put("processedAt",
                    java.time.LocalDateTime.now().toString());
            return item;
        };
    }

    @Bean
    public ItemWriter<Map<String, Object>> orderReportWriter() {
        return items -> {
            log.info("Batch: Writing {} order report records", items.size());
            items.forEach(item ->
                log.debug("Report record: Order {} - Amount: {}", item.get("orderId"), item.get("amount")));
            // In production: write to report table, or generate CSV/PDF
        };
    }

    // ==================== CLEANUP JOB ====================

    @Bean
    public Job cleanupJob() {
        return new JobBuilder("cleanupJob", jobRepository)
                .start(cleanupStep())
                .build();
    }

    @Bean
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
                .<String, String>chunk(100, transactionManager)
                .reader(cleanupReader())
                .processor(cleanupProcessor())
                .writer(cleanupWriter())
                .build();
    }

    @Bean
    public ItemReader<String> cleanupReader() {
        return new ListItemReader<>(List.of("expired_token_1", "expired_token_2"));
    }

    @Bean
    public ItemProcessor<String, String> cleanupProcessor() {
        return token -> {
            log.debug("Processing cleanup for: {}", token);
            return token;
        };
    }

    @Bean
    public ItemWriter<String> cleanupWriter() {
        return items -> log.info("Cleanup: Processed {} records", items.size());
    }
}
