package com.example.cloudBalanceBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {
    @Id
    @Column(length = 36)
    private String id;

    private String name;
    private String provider;
    private String providerAccountId;

    @Column(columnDefinition = "text")
    private String meta;

    private String status;   // NEW field

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserAccount> user = new HashSet<>();

    // Custom constructor for onboarding
    public Account(String id) {
        this.id = id;
        this.status = "ORPHAN";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
