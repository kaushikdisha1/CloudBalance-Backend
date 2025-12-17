package com.example.cloudBalanceBackend.repository;

import com.example.cloudBalanceBackend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
}