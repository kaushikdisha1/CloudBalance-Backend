package com.example.cloudBalanceBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
@Entity
@Table(name = "csv_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvJob {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private String actorId;

    private Integer successCount;
    private Integer failureCount;

    private String errorMessage;

    private Instant createdAt;
    private Instant updatedAt;
}
