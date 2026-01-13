package com.example.cloudBalanceBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @Column(length = 50)
    private String id;

    private String firstName;
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserAccount> account = new ArrayList<>();

    // convenience full name
    @Transient
    public String getName() {
        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) return null;
        if (firstName == null || firstName.isBlank()) return lastName;
        if (lastName == null || lastName.isBlank()) return firstName;
        return firstName + " " + lastName;
    }
}