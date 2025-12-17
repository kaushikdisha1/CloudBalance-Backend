package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.model.AuditLog;
import com.example.cloudBalanceBackend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String actorId, String details) {

        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID().toString())
                .actorId(actorId)
                .action(action)
                .details(details)
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
    }
}
