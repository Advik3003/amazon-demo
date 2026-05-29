package com.amazondemo.batch.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Batch Job Scheduler
 * ====================
 * Uses Spring's @Scheduled to trigger batch jobs on a cron schedule.
 *
 * CRON FORMAT: second minute hour day month weekday
 * "0 0 1 * * ?" = At 01:00:00 every day
 * "0 0/30 * * * ?" = Every 30 minutes (using 0/30 instead of star/30 to avoid comment termination)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("orderReportJob")
    private final Job orderReportJob;

    @Qualifier("cleanupJob")
    private final Job cleanupJob;

    /**
     * Run order report daily at 1 AM
     */
    @Scheduled(cron = "${app.batch.order-report-cron:0 0 1 * * ?}")
    public void runOrderReportJob() {
        log.info("Starting scheduled order report job");
        try {
            jobLauncher.run(orderReportJob, new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            log.error("Order report job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Run cleanup daily at 2 AM
     */
    @Scheduled(cron = "${app.batch.cleanup-cron:0 0 2 * * ?}")
    public void runCleanupJob() {
        log.info("Starting scheduled cleanup job");
        try {
            jobLauncher.run(cleanupJob, new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            log.error("Cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
