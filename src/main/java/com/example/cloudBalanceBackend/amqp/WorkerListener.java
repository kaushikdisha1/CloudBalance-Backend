package com.example.cloudBalanceBackend.amqp;

import com.example.cloudBalanceBackend.repository.CsvJobRepository;
import com.example.cloudBalanceBackend.model.CsvJob;
import com.example.cloudBalanceBackend.model.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerListener {

    private final RabbitPublisher publisher;
    private final CsvJobRepository csvJobRepo;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitConfig.BULK_CSV_QUEUE)
    public void handleBulkJob(Map<String, Object> payload) {
        String jobId = null;
        try {
            if (!"bulk_csv_job".equals(payload.get("type"))) {
                log.warn("Ignoring message with unexpected type: {}", payload.get("type"));
                return;
            }

            jobId = (String) payload.get("jobId");
            String csvBase64 = (String) payload.get("csv");
            String actorId = (String) payload.get("actorId");

            log.info("Processing bulk CSV job: {} by actor: {}", jobId, actorId);

            // Update job status to PROCESSING
            updateJobStatus(jobId, JobStatus.PROCESSING, null, 0, 0);

            String csvText = new String(Base64.getDecoder().decode(csvBase64));
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withTrim()
                    .parse(new StringReader(csvText));

            int successCount = 0;
            int failureCount = 0;
            List<String> errors = new ArrayList<>();

            for (CSVRecord r : parser) {
                try {
                    long recordNum = r.getRecordNumber();
                    String name = r.get("name");
                    String email = r.get("email");
                    String password = r.get("password");
                    String role = r.get("role");
                    String accountIds = r.isMapped("accountIds") ? r.get("accountIds") : null;

                    // Validate required fields
                    if (name == null || name.isBlank()) {
                        String error = "Row " + recordNum + ": Missing name";
                        errors.add(error);
                        log.warn(error);
                        failureCount++;
                        continue;
                    }
                    if (email == null || email.isBlank()) {
                        String error = "Row " + recordNum + ": Missing email";
                        errors.add(error);
                        log.warn(error);
                        failureCount++;
                        continue;
                    }
                    if (password == null || password.isBlank()) {
                        String error = "Row " + recordNum + ": Missing password";
                        errors.add(error);
                        log.warn(error);
                        failureCount++;
                        continue;
                    }
                    if (role == null || role.isBlank()) {
                        String error = "Row " + recordNum + ": Missing role";
                        errors.add(error);
                        log.warn(error);
                        failureCount++;
                        continue;
                    }

                    // Validate role
                    if (!Set.of("ADMIN", "READ_ONLY", "CUSTOMER").contains(role)) {
                        String error = "Row " + recordNum + ": Invalid role: " + role;
                        errors.add(error);
                        log.warn(error);
                        failureCount++;
                        continue;
                    }

                    // Parse account IDs
                    List<String> aids = new ArrayList<>();
                    if (accountIds != null && !accountIds.isBlank()) {
                        for (String a : accountIds.split(",")) {
                            String trimmed = a.trim();
                            if (!trimmed.isBlank()) {
                                aids.add(trimmed);
                            }
                        }
                    }

                    // Publish user creation message
                    Map<String, Object> createUserPayload = Map.of(
                            "type", "create_user",
                            "user", Map.of(
                                    "name", name,
                                    "email", email,
                                    "password", password,
                                    "role", role,
                                    "accountIds", aids
                            ),
                            "actorId", actorId
                    );
                    publisher.publishCreateUser(createUserPayload);
                    successCount++;
                    log.debug("Published user creation for: {}", email);

                } catch (Exception e) {
                    String error = "Row " + r.getRecordNumber() + ": " + e.getMessage();
                    errors.add(error);
                    log.error(error, e);
                    failureCount++;
                }
            }

            log.info("Completed CSV job {}: {} success, {} failures", jobId, successCount, failureCount);

            // Update job status to COMPLETED
            String errorMessage = errors.isEmpty() ? null : String.join("; ", errors);
            updateJobStatus(jobId, JobStatus.COMPLETED, errorMessage, successCount, failureCount);

        } catch (Exception e) {
            log.error("Failed to process bulk CSV job {}: {}", jobId, e.getMessage(), e);
            if (jobId != null) {
                updateJobStatus(jobId, JobStatus.FAILED, e.getMessage(), 0, 0);
            }
        }
    }

    private void updateJobStatus(String jobId, JobStatus status, String errorMessage, int successCount, int failureCount) {
        try {
            Optional<CsvJob> jobOpt = csvJobRepo.findById(jobId);
            if (jobOpt.isPresent()) {
                CsvJob job = jobOpt.get();
                job.setStatus(status);
                job.setSuccessCount(successCount);
                job.setFailureCount(failureCount);
                job.setErrorMessage(errorMessage);
                job.setUpdatedAt(Instant.now());
                csvJobRepo.save(job);
                log.debug("Updated job {} status to {}", jobId, status);
            } else {
                log.warn("Job {} not found in database", jobId);
            }
        } catch (Exception e) {
            log.error("Failed to update job status for {}: {}", jobId, e.getMessage(), e);
        }
    }
}