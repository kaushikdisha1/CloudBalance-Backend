package com.example.cloudBalanceBackend.bootstrap;

import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Seeds a single Admin user if none exists.
 */
@Component
public class AdminDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminDataLoader(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean adminExists = userRepository.findByEmail("admin@cloudbalance.local").isPresent();

        if (!adminExists) {
            User admin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .firstName("Administrator")
                    .lastName("")
                    .email("admin@cloudbalance.local")
                    .passwordHash(passwordEncoder.encode("Admin@12345"))
                    .role(Role.ADMIN)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepository.save(admin);
            System.out.println("Seeded initial admin -> email: admin@cloudbalance.local  password: Admin@12345");
        } else {
            System.out.println("Admin user exists - skipping seed");
        }
    }
}