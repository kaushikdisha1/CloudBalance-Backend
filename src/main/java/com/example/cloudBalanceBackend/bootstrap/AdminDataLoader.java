package com.example.cloudBalanceBackend.bootstrap;

import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
//@Component
@RequiredArgsConstructor
public class AdminDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking for default admin user...");

        boolean adminExists = userRepository.findByEmail("admin@cloudbalance.local").isPresent();

        if (!adminExists) {
            User admin = User.builder()
                    .id(UUID.randomUUID().toString())
                    .firstName("Administrator")
                    .lastName("")
                    .email("admin@cloudbalance.com")
                    .passwordHash(passwordEncoder.encode("Admin@12345"))
                    .role(Role.ADMIN)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepository.save(admin);

            log.info("✅ Seeded initial admin user");
            log.info("   Email: admin@cloudbalance.local");
            log.info("   Password: Admin@12345");
            log.info("   ⚠️  CHANGE PASSWORD IN PRODUCTION!");
        } else {
            log.info("✅ Admin user already exists - skipping seed");
        }
    }
}