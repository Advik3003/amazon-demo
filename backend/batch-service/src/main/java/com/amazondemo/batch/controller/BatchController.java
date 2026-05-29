package com.amazondemo.batch.controller;

import com.amazondemo.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Batch Controller
 * Allows manual triggering of batch jobs via HTTP (useful for testing and admin use).
 */
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Jobs", description = "Manually trigger batch processing jobs")
public class BatchController {

    private final JobLauncher jobLauncher;

    @Qualifier("orderReportJob")
    private final Job orderReportJob;

    @Qualifier("cleanupJob")
    private final Job cleanupJob;

    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<ApiResponse<Map<String, String>>> runJob(@PathVariable String jobName) {
        try {
            Job job = switch (jobName) {
                case "order-report" -> orderReportJob;
                case "cleanup" -> cleanupJob;
                default -> throw new IllegalArgumentException("Unknown job: " + jobName);
            };

            var execution = jobLauncher.run(job, new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters());

            log.info("Job {} triggered manually - ExecutionId: {}", jobName, execution.getId());

            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("jobName", jobName, "executionId", execution.getId().toString(),
                            "status", execution.getStatus().toString()),
                    "Job started"));
        } catch (Exception e) {
            log.error("Failed to run job: {}", jobName, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Job failed: " + e.getMessage(), null));
        }
    }
}
