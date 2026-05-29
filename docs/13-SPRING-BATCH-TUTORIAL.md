# Spring Batch Tutorial

> **What you'll learn**: Spring Batch fundamentals, how it's used in this project
> for reports and data sync, and what interviewers expect you to know.

---

## 1. What is Spring Batch?

Spring Batch is a framework for **processing large volumes of data** in scheduled,
automated jobs.

**When to use Spring Batch:**
```
✓ Generate daily/weekly reports (sales, inventory)
✓ Export data to CSV/Excel for analysis
✓ Data migration (import from old system)
✓ Data synchronization between systems
✓ Send bulk emails at scheduled times
✗ NOT for real-time processing (use Kafka instead)
```

**In this project:**
```
Daily Sales Report     → Reads orders from PostgreSQL → Aggregates → Writes to S3/file
Weekly Inventory Sync  → Reads inventory → Checks low stock → Sends alerts
Product Data Export    → Reads products → Writes CSV
Order Cleanup          → Archives old completed orders
```

---

## 2. Spring Batch Architecture

```
JOB
└── STEP 1 ──────────────────────────────────────────────────────────
    ├── ITEM READER   → Read data in chunks (from DB, file, API)
    ├── ITEM PROCESSOR → Transform/filter/validate each item
    └── ITEM WRITER   → Write transformed data (to DB, file, S3)

└── STEP 2 (Tasklet — simple one-shot logic, no chunks)
    └── Send email notification with report summary
```

**Chunk-oriented processing:**
```
Read 10 items → Process 10 items → Write 10 items
Read 10 items → Process 10 items → Write 10 items
... until no more items

chunk-size = 10 means:
- Items are buffered in memory
- Committed to DB/file every 10 items
- If step 5 of chunk N fails → only that chunk is retried
```

---

## 3. Daily Sales Report — Full Implementation

### Job Configuration

```java
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class SalesReportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // ==================== JOB ====================
    @Bean
    public Job salesReportJob(Step readOrdersStep, Step generateReportStep) {
        return new JobBuilder("salesReportJob", jobRepository)
            .incrementer(new RunIdIncrementer())       // Auto-increment run ID
            .start(readOrdersStep)
            .next(generateReportStep)
            .listener(new SalesReportJobListener())    // Before/after hooks
            .build();
    }

    // ==================== STEP 1: Read + Process + Write ====================
    @Bean
    public Step readOrdersStep(
            ItemReader<Order> orderReader,
            ItemProcessor<Order, SalesRecord> orderProcessor,
            ItemWriter<SalesRecord> salesRecordWriter) {

        return new StepBuilder("readOrdersStep", jobRepository)
            .<Order, SalesRecord>chunk(100, transactionManager)  // Process 100 at a time
            .reader(orderReader)
            .processor(orderProcessor)
            .writer(salesRecordWriter)
            .faultTolerant()
                .skipLimit(10)                       // Skip up to 10 bad records
                .skip(DataIntegrityViolationException.class)
                .retryLimit(3)                       // Retry 3 times on transient errors
                .retry(TransientDataAccessException.class)
            .listener(new OrderProcessingListener())
            .build();
    }

    // ==================== STEP 2: Tasklet (simple task) ====================
    @Bean
    public Step generateReportStep(Tasklet reportTasklet) {
        return new StepBuilder("generateReportStep", jobRepository)
            .tasklet(reportTasklet, transactionManager)
            .build();
    }
}
```

### Item Reader (JdbcPagingItemReader — reads from PostgreSQL)

```java
@Configuration
public class OrderReaderConfig {

    @Bean
    @StepScope  // ← Scope = per step execution (can use job parameters)
    public JdbcPagingItemReader<Order> orderReader(
            DataSource dataSource,
            @Value("#{jobParameters['reportDate']}") String reportDate) {

        return new JdbcPagingItemReaderBuilder<Order>()
            .name("orderReader")
            .dataSource(dataSource)
            .selectClause("SELECT id, user_id, total_amount, status, created_at")
            .fromClause("FROM orders")
            .whereClause("WHERE DATE(created_at) = :reportDate AND status = 'COMPLETED'")
            .parameterValues(Map.of("reportDate", reportDate))
            .sortKeys(Map.of("id", Order.ASCENDING))
            .pageSize(100)          // Read 100 rows per DB query
            .rowMapper(new OrderRowMapper())
            .build();
    }
}

// Custom RowMapper
class OrderRowMapper implements RowMapper<Order> {
    @Override
    public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Order.builder()
            .id(rs.getString("id"))
            .userId(rs.getString("user_id"))
            .totalAmount(rs.getBigDecimal("total_amount"))
            .status(rs.getString("status"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();
    }
}
```

### Item Processor (Business Logic)

```java
@Component
@Slf4j
public class OrderToSalesRecordProcessor implements ItemProcessor<Order, SalesRecord> {

    @Override
    public SalesRecord process(Order order) throws Exception {
        // Return null to FILTER OUT the item (won't be written)
        if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Skipping order with zero/negative amount: {}", order.getId());
            return null;  // Filtered out
        }

        return SalesRecord.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .amount(order.getTotalAmount())
            .date(order.getCreatedAt().toLocalDate())
            .build();
    }
}
```

### Item Writer (Write to file)

```java
@Configuration
public class SalesRecordWriterConfig {

    @Bean
    @StepScope
    public FlatFileItemWriter<SalesRecord> salesRecordWriter(
            @Value("#{jobParameters['outputFile']}") String outputFile) {

        return new FlatFileItemWriterBuilder<SalesRecord>()
            .name("salesRecordWriter")
            .resource(new FileSystemResource(outputFile))
            .headerCallback(writer -> writer.write("OrderId,UserId,Amount,Date"))
            .lineAggregator(new DelimitedLineAggregator<SalesRecord>() {{
                setDelimiter(",");
                setFieldExtractor(new BeanWrapperFieldExtractor<>() {{
                    setNames(new String[]{"orderId", "userId", "amount", "date"});
                }});
            }})
            .build();
    }

    // Alternative: Write to S3 (staging/prod)
    @Bean
    @StepScope
    @ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
    public ItemWriter<SalesRecord> s3SalesRecordWriter(S3Client s3Client,
            @Value("${app.aws.s3.reports-bucket}") String bucket,
            @Value("#{jobParameters['reportDate']}") String reportDate) {

        return items -> {
            StringBuilder csv = new StringBuilder("OrderId,UserId,Amount,Date\n");
            items.forEach(item ->
                csv.append(item.getOrderId()).append(",")
                   .append(item.getUserId()).append(",")
                   .append(item.getAmount()).append(",")
                   .append(item.getDate()).append("\n")
            );

            String key = "sales-reports/sales-" + reportDate + ".csv";
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromString(csv.toString())
            );
            log.info("Sales report uploaded to S3: {}/{}", bucket, key);
        };
    }
}
```

### Job Listener (Before/After hooks)

```java
@Slf4j
public class SalesReportJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Sales report job starting: {}", jobExecution.getJobInstance().getJobName());
        log.info("Parameters: {}", jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Sales report completed successfully. Records: {}",
                jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getWriteCount)
                    .sum());
        } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Sales report FAILED: {}",
                jobExecution.getAllFailureExceptions());
            // Alert ops team
        }
    }
}
```

---

## 4. Scheduling Jobs

### Spring Scheduler

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job salesReportJob;
    private final Job inventorySyncJob;

    // Run every day at 2 AM
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailySalesReport() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("reportDate", LocalDate.now().minusDays(1).toString())
            .addString("outputFile", "/reports/sales-" + LocalDate.now() + ".csv")
            .addLong("timestamp", System.currentTimeMillis())  // Makes params unique
            .toJobParameters();

        JobExecution execution = jobLauncher.run(salesReportJob, params);
        log.info("Sales report job status: {}", execution.getStatus());
    }

    // Run every Monday at 3 AM
    @Scheduled(cron = "0 0 3 ? * MON")
    public void runWeeklyInventorySync() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        jobLauncher.run(inventorySyncJob, params);
    }
}
```

### REST API to trigger jobs manually

```java
@RestController
@RequestMapping("/api/v1/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job salesReportJob;

    @PostMapping("/sales-report")
    public ResponseEntity<String> triggerSalesReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) throws Exception {

        JobParameters params = new JobParametersBuilder()
            .addString("reportDate", date.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        JobExecution execution = jobLauncher.run(salesReportJob, params);
        return ResponseEntity.ok("Job started: " + execution.getId());
    }

    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<String> getJobStatus(@PathVariable Long jobExecutionId) {
        // Query JobRepository for execution status
        return ResponseEntity.ok("COMPLETED");
    }
}
```

---

## 5. Spring Batch Metadata Tables

Spring Batch creates tables in the database to track job execution history:

```sql
BATCH_JOB_INSTANCE      → Records each job (name + parameters)
BATCH_JOB_EXECUTION     → Each run of a job (start time, end time, status)
BATCH_JOB_EXECUTION_PARAMS → Parameters for each run
BATCH_STEP_EXECUTION    → Each step within a job run (read/write/skip counts)
BATCH_STEP_EXECUTION_CONTEXT → Saved state for step restart
BATCH_JOB_EXECUTION_CONTEXT  → Saved state for job restart
```

These tables enable **restartability**:
```
Job runs 5000 records, processes 3000, then FAILS
→ On next run, Spring Batch knows it processed 3000
→ Restarts from record 3001 (not from 0!)
```

---

## 6. Interview Questions: Spring Batch

**Q: What is the difference between a Tasklet and a Chunk in Spring Batch?**
> - **Chunk**: Read-Process-Write cycle repeated in batches. Good for large datasets.
>   Reader reads one item at a time, Processor transforms it, then all items in a chunk are written together.
> - **Tasklet**: A single task that runs once (not chunked). Good for: cleanup, SQL execution, sending notifications, file moves.

**Q: What is restartability in Spring Batch?**
> Spring Batch tracks job execution state in its metadata tables. If a job fails partway through, it can be restarted and Spring Batch will skip already-processed records and continue from where it left off. Requires items to have a consistent read order.

**Q: What is ItemSkipPolicy and when do you use it?**
> Controls which exceptions cause a record to be skipped vs causing the step to fail.
> Use when you have a dataset with a few bad records (e.g., import file where 5 out of 10,000 rows have corrupt data — skip those 5, process the rest).

**Q: How would you process 10 million records efficiently in Spring Batch?**
> 1. Use `JdbcPagingItemReader` with paginated queries (never load all at once)
> 2. Set chunk size around 100-500 (balance between memory and DB commits)
> 3. Enable parallel processing with `AsyncItemProcessor` and `AsyncItemWriter`
> 4. Partition the job across multiple threads/nodes using `Partitioner`
> 5. For distributed processing, use Spring Batch integration with Spring Cloud Task
