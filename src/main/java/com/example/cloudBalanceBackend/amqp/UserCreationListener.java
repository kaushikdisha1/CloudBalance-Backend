package com.example.cloudBalanceBackend.amqp;

import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import com.example.cloudBalanceBackend.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class UserCreationListener {

    private final UserRepository userRepo;
    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogRepository auditRepo;
    private final BCryptPasswordEncoder encoder;

    private String[] splitName(String name) {
        if (name == null) return new String[]{null, null};
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return new String[]{null, null};
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return new String[]{parts[0], null};
        String first = parts[0];
        String last = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        return new String[]{first, last};
    }

    @RabbitListener(queues = RabbitConfig.USER_CREATION_QUEUE)
    public void handleCreateUser(Map<String, Object> payload) {
        try {
            if (!"create_user".equals(payload.get("type"))) {
                log.warn("Ignoring message with unexpected type: {}", payload.get("type"));
                return;
            }

            Map<String, Object> user = (Map<String, Object>) payload.get("user");
            String actorId = (String) payload.get("actorId");
            String email = (String) user.get("email");
            String password = (String) user.get("password");

            log.info("Processing user creation for email: {}", email);

            // Validate email and password
            try {
                ValidationUtils.validateEmail(email);
                ValidationUtils.validatePassword(password);
            } catch (IllegalArgumentException e) {
                log.error("Validation failed for user {}: {}", email, e.getMessage());
                return;
            }

            // Check for duplicate
            if (userRepo.existsByEmail(email)) {
                log.warn("Skipping duplicate user: {}", email);
                return;
            }

            // Create user
            String id = UUID.randomUUID().toString();
            String name = (String) user.get("name");
            String roleStr = (String) user.get("role");
            Role role = Role.valueOf(roleStr);

            String[] parts = splitName(name);

            User u = User.builder()
                    .id(id)
                    .firstName(parts[0])
                    .lastName(parts[1])
                    .email(email)
                    .passwordHash(encoder.encode(password))
                    .role(role)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepo.save(u);

            log.info("Created user: {} with role: {}", email, role);

            // Assign accounts
            List<String> aids = (List<String>) user.getOrDefault("accountIds", List.of());
            int assignedCount = 0;
            for (String aid : aids) {
                Optional<Account> accOpt = accountRepo.findById(aid);
                if (accOpt.isPresent()) {
                    Account acc = accOpt.get();
                    UserAccount ua = UserAccount.builder()
                            .id(UUID.randomUUID().toString())
                            .user(u)
                            .account(acc)
                            .assignedAt(Instant.now())
                            .build();
                    uaRepo.save(ua);
                    assignedCount++;

                    // Update account status from ORPHAN to ASSIGNED
                    if ("ORPHAN".equals(acc.getStatus())) {
                        acc.setStatus("ASSIGNED");
                        acc.setUpdatedAt(Instant.now());
                        accountRepo.save(acc);
                        log.debug("Account {} status changed from ORPHAN to ASSIGNED", aid);
                    }
                } else {
                    log.warn("Account not found: {}, skipping assignment", aid);
                }
            }

            log.info("Assigned {} accounts to user: {}", assignedCount, email);

            // Create audit log
            AuditLog auditLog = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .actorId(actorId)
                    .action("ASYNC_CREATE_USER")
                    .details("Created user " + email + " with role " + role + " and " + assignedCount + " accounts")
                    .createdAt(Instant.now())
                    .build();
            auditRepo.save(auditLog);

            log.info("Successfully created user from MQ: {}", email);

        } catch (Exception e) {
            log.error("Failed to process user creation message: {}", e.getMessage(), e);
        }
    }
}