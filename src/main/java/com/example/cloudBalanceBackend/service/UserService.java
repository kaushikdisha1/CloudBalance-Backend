package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.dto.UserDto;
import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import com.example.cloudBalanceBackend.amqp.RabbitPublisher;
import com.example.cloudBalanceBackend.utils.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import com.example.cloudBalanceBackend.model.Role;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final UserAccountRepository uaRepo;
    private final AccountRepository accountRepo;
    private final AuditLogger auditLogger;
    private final BCryptPasswordEncoder encoder;
    private final RabbitPublisher publisher;
    private final CsvJobRepository csvJobRepo;

    public boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.warn("No authentication context found");
            return false;
        }
        boolean hasRole = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
        log.debug("Checking role {}: {}", role, hasRole);
        return hasRole;
    }

    public boolean hasAnyRole(List<Role> roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.warn("No authentication context found");
            return false;
        }
        Set<String> roleSet = roles.stream()
                .map(r -> "ROLE_" + r.name())
                .collect(Collectors.toSet());
        boolean hasAnyRole = auth.getAuthorities().stream()
                .anyMatch(a -> roleSet.contains(a.getAuthority()));
        log.debug("Checking any of roles {}: {}", roles, hasAnyRole);
        return hasAnyRole;
    }

    public Page<UserDto> listUsers(Pageable pageable) {
        log.info("Listing users: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<UserDto> users = userRepo.findAll(pageable).map(UserDto::fromEntity);
        log.info("Found {} users", users.getTotalElements());
        return users;
    }

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

    @Transactional
    public User createUserSync(String name, String email, String password, Role role, List<String> accountIds, String actorId) {
        log.info("Creating user synchronously: email={}, role={}", email, role);

        ValidationUtils.validatePassword(password);
        ValidationUtils.validateEmail(email);

        if (userRepo.existsByEmail(email)) {
            log.error("User already exists with email: {}", email);
            throw new IllegalArgumentException("User already exists");
        }

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
        log.info("Created user: {} with ID: {}", email, u.getId());

        if (accountIds != null && !accountIds.isEmpty()) {
            int assignedCount = 0;
            for (String aid : accountIds) {
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
        }

        auditLogger.log("CREATE_USER", actorId, "Created user " + u.getEmail() + " with role " + role);
        return u;
    }

    @Transactional
    public User updateUser(String userId, String name, String email, Role role, List<String> accountIds, String actorId) {
        log.info("Updating user: userId={}", userId);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found: {}", userId);
                    return new IllegalArgumentException("User not found");
                });

        // Update basic fields
        if (name != null) {
            String[] nameParts = splitName(name);
            user.setFirstName(nameParts[0]);
            user.setLastName(nameParts[1]);
            log.debug("Updated name for user {}: {} {}", userId, nameParts[0], nameParts[1]);
        }

        if (email != null) {
            ValidationUtils.validateEmail(email);
            if (!email.equals(user.getEmail()) && userRepo.existsByEmail(email)) {
                log.error("Email already in use: {}", email);
                throw new IllegalArgumentException("Email already in use");
            }
            user.setEmail(email);
            log.debug("Updated email for user {}: {}", userId, email);
        }

        if (role != null) {
            user.setRole(role);
            log.debug("Updated role for user {}: {}", userId, role);
        }

        user.setUpdatedAt(Instant.now());
        userRepo.save(user);

        // Update account assignments if provided
        if (accountIds != null) {
            // Remove old assignments
            List<UserAccount> oldAssignments = uaRepo.findByUserId(userId);
            for (UserAccount ua : oldAssignments) {
                Account acc = ua.getAccount();
                uaRepo.delete(ua);

                // Check if account becomes orphan
                if (uaRepo.findByAccountId(acc.getId()).isEmpty()) {
                    acc.setStatus("ORPHAN");
                    acc.setUpdatedAt(Instant.now());
                    accountRepo.save(acc);
                    log.debug("Account {} became orphan after unassignment", acc.getId());
                }
            }
            log.info("Removed {} old account assignments for user {}", oldAssignments.size(), userId);

            // Add new assignments
            int assignedCount = 0;
            for (String aid : accountIds) {
                Optional<Account> accOpt = accountRepo.findById(aid);
                if (accOpt.isPresent()) {
                    Account acc = accOpt.get();
                    UserAccount ua = UserAccount.builder()
                            .id(UUID.randomUUID().toString())
                            .user(user)
                            .account(acc)
                            .assignedAt(Instant.now())
                            .build();
                    uaRepo.save(ua);
                    assignedCount++;

                    // Update account status
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
            log.info("Assigned {} new accounts to user {}", assignedCount, userId);
        }

        auditLogger.log("UPDATE_USER", actorId, "Updated user " + userId);
        log.info("Successfully updated user: {}", userId);
        return user;
    }

    public long countUsers() {
        return userRepo.count();
    }

    public User getUserById(String userId) {
        return userRepo.findById(userId).orElse(null);
    }


    public List<AccountDto> getUserAccounts(String userId) {
        log.info("Fetching accounts for user: {}", userId);
        List<AccountDto> accounts = uaRepo.findByUserId(userId)
                .stream()
                .map(UserAccount::getAccount)
                .map(AccountDto::new)
                .collect(Collectors.toList());
        log.info("Found {} accounts for user {}", accounts.size(), userId);
        return accounts;
    }

    @Transactional
    public void assignAccounts(String userId, List<String> accountIds, String actorId) {
        log.info("Assigning accounts to user {}: accountIds={}", userId, accountIds);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found: {}", userId);
                    return new IllegalArgumentException("User not found");
                });

        if (user.getRole() != Role.CUSTOMER) {
            log.error("Cannot assign accounts to non-CUSTOMER user: {} with role {}", userId, user.getRole());
            throw new IllegalArgumentException("Can only assign to CUSTOMER role users");
        }

        int assignedCount = 0;
        for (String aid : accountIds) {
            Optional<Account> accOpt = accountRepo.findById(aid);
            if (accOpt.isPresent()) {
                Account acc = accOpt.get();
                Optional<UserAccount> existing = uaRepo.findByUserIdAndAccountId(userId, aid);
                if (existing.isEmpty()) {
                    UserAccount ua = UserAccount.builder()
                            .id(UUID.randomUUID().toString())
                            .user(user)
                            .account(acc)
                            .assignedAt(Instant.now())
                            .build();
                    uaRepo.save(ua);
                    assignedCount++;

                    // Update account status
                    if ("ORPHAN".equals(acc.getStatus())) {
                        acc.setStatus("ASSIGNED");
                        acc.setUpdatedAt(Instant.now());
                        accountRepo.save(acc);
                        log.debug("Account {} status changed from ORPHAN to ASSIGNED", aid);
                    }
                } else {
                    log.debug("Account {} already assigned to user {}", aid, userId);
                }
            } else {
                log.warn("Account not found: {}, skipping assignment", aid);
            }
        }

        auditLogger.log("ASSIGN_ACCOUNTS", actorId, "Assigned " + assignedCount + " accounts to user " + userId);
        log.info("Successfully assigned {} accounts to user {}", assignedCount, userId);
    }

    public String enqueueCsvBulk(byte[] csvBytes, String actorId) {
        String jobId = UUID.randomUUID().toString();
        log.info("Enqueueing CSV bulk job: jobId={}, size={} bytes, actor={}", jobId, csvBytes.length, actorId);

        CsvJob job = CsvJob.builder()
                .id(jobId)
                .status(JobStatus.PENDING)
                .actorId(actorId)
                .successCount(0)
                .failureCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        csvJobRepo.save(job);
        log.info("Created CSV job record: {}", jobId);

        publisher.publishBulkCsvJob(
                jobId,
                Base64.getEncoder().encodeToString(csvBytes),
                actorId
        );
        log.info("Published CSV job to queue: {}", jobId);

        return jobId;
    }

    public Optional<CsvJob> getCsvJobStatus(String jobId) {
        log.debug("Fetching CSV job status: {}", jobId);
        Optional<CsvJob> job = csvJobRepo.findById(jobId);
        if (job.isEmpty()) {
            log.warn("CSV job not found: {}", jobId);
        }
        return job;
    }
}