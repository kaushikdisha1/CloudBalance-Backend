package com.example.cloudBalanceBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {
    @Id
    @Column(length = 36)
    private String id;

    @Column(unique = true, nullable = false, length = 1024)
    private String token;

    private Instant expiresAt;
    private Instant createdAt;
}