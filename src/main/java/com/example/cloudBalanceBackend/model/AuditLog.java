package com.example.cloudBalanceBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @Column(length = 36)
    private String id;

    private String actorId;
    private String action;

    @Column(columnDefinition = "text")
    private String details;

    private Instant createdAt;
}