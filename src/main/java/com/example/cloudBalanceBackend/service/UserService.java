package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.UserDto;
import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import com.example.cloudBalanceBackend.amqp.RabbitPublisher;
import com.example.cloudBalanceBackend.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final UserAccountRepository uaRepo;
    private final AccountRepository accountRepo;
    private final AuditLogger auditLogger;
    private final BCryptPasswordEncoder encoder;
    private final RabbitPublisher publisher;

    public boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    public boolean hasAnyRole(List<Role> roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        Set<String> rs = roles.stream().map(r -> "ROLE_" + r.name()).collect(Collectors.toSet());
        return auth.getAuthorities().stream().anyMatch(a -> rs.contains(a.getAuthority()));
    }

    // Return paginated list of users
    public Page<UserDto> listUsers(Pageable pageable) {
        return userRepo.findAll(pageable).map(UserDto::fromEntity);
    }

    // Helper to split an incoming single "name" into first & last
    private String[] splitName(String name) {
        if (name == null) return new String[]{null, null};
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return new String[]{null, null};
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) return new String[]{parts[0], null};
        String first = parts[0];
        String last = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        return new String[]{first, last};
    }

    public User createUserSync(String name, String email, String password, Role role, List<String> accountIds, String actorId) {
        ValidationUtils.validatePassword(password);
        ValidationUtils.validateEmail(email);
        if (userRepo.existsByEmail(email)) throw new IllegalArgumentException("User already exists");

        String[] nameParts = splitName(name);

        User u = User.builder()
                .id(UUID.randomUUID().toString())
                .firstName(nameParts[0])
                .lastName(nameParts[1])
                .email(email)
                .passwordHash(encoder.encode(password))
                .role(role)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepo.save(u);

        if (accountIds != null && !accountIds.isEmpty()) {
            for (String aid : accountIds) {
                accountRepo.findById(aid).ifPresent(acc -> {
                    UserAccount ua = UserAccount.builder()
                            .id(UUID.randomUUID().toString())
                            .user(u)
                            .account(acc)
                            .assignedAt(Instant.now())
                            .build();
                    uaRepo.save(ua);
                });
            }
        }

        auditLogger.log(
                "CREATE_USER",
                actorId,
                "created user " + u.getEmail()
        );
        return u;
    }

    public void assignAccounts(String userId, List<String> accountIds, String actorId) {
        var user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != Role.CUSTOMER) throw new IllegalArgumentException("Can only assign to CUSTOMER role users");
        for (String aid : accountIds) {
            accountRepo.findById(aid).ifPresent(acc -> {
                var existing = uaRepo.findByUserIdAndAccountId(userId, aid);
                if (existing.isEmpty()) {
                    UserAccount ua = UserAccount.builder()
                            .id(UUID.randomUUID().toString())
                            .user(user)
                            .account(acc)
                            .assignedAt(Instant.now())
                            .build();
                    uaRepo.save(ua);
                }
            });
        }
        auditLogger.log(
                "ASSIGN_ACCOUNTS",
                actorId,
                "assigned accounts to " + userId
        );
    }

    public String enqueueCsvBulk(byte[] csvBytes, String actorId) {
        String jobId = UUID.randomUUID().toString();
        publisher.publishBulkCsvJob(jobId, Base64.getEncoder().encodeToString(csvBytes), actorId);
        return jobId;
    }
}