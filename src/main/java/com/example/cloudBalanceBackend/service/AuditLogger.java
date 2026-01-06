package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.model.AuditLog;
import com.example.cloudBalanceBackend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String actorId, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .actorId(actorId)
                    .action(action)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log created: action={}, actor={}, details={}", action, actorId, details);
        } catch (Exception e) {
            // Don't fail the main operation if audit logging fails
            log.error("Failed to create audit log: action={}, actor={}, error={}",
                    action, actorId, e.getMessage(), e);
        }
    }
}