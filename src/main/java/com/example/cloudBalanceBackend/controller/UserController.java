package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.service.UserService;
import com.example.cloudBalanceBackend.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Enhanced User Controller using SecurityUtils
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Helper method to get current user's ID
     * Now uses SecurityUtils instead of manual extraction
     */
    private String getActorId() {

        return SecurityUtils.getCurrentUserId();
    }

    /**
     * Get current user info endpoint
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        User currentUser = SecurityUtils.getCurrentUser();

        if (currentUser == null) {
            log.error("Unable to get current user");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        log.info("Current user info requested: {}", currentUser.getEmail());

        return ResponseEntity.ok(Map.of(
                "id", currentUser.getId(),
                "email", currentUser.getEmail(),
                "firstName", currentUser.getFirstName() != null ? currentUser.getFirstName() : "",
                "lastName", currentUser.getLastName() != null ? currentUser.getLastName() : "",
                "role", currentUser.getRole().name(),
                "createdAt", currentUser.getCreatedAt().toString()
        ));
    }

    /**
     * Admin & ReadOnly: list users with pagination
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY')")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (!userService.hasAnyRole(List.of(Role.ADMIN, Role.READ_ONLY))) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        var pageable = PageRequest.of(page, size);
        var users = userService.listUsers(pageable);

        log.info("Listing users: page={}, size={}, total={}",
                page, size, users.getTotalElements());

        return ResponseEntity.ok(users);
    }

    /**
     * Admin: create user
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String roleStr = (String) body.get("role");

        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) body.getOrDefault("accountIds", List.of());

        Role role = Role.valueOf(roleStr);

        String actorId = getActorId();
        log.info("Admin {} creating new user: {}", actorId, email);

        var u = userService.createUserSync(name, email, password, role, accountIds, actorId);

        return ResponseEntity.status(201).body(Map.of(
                "id", u.getId(),
                "email", u.getEmail()
        ));
    }

    /**
     * Admin: update user
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body
    ) {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String roleStr = (String) body.get("role");

        @SuppressWarnings("unchecked")
        List<String> accountIds = (List<String>) body.getOrDefault("accountIds", List.of());

        Role role = roleStr != null ? Role.valueOf(roleStr) : null;

        String actorId = getActorId();
        log.info("Admin {} updating user: {}", actorId, userId);

        var updated = userService.updateUser(userId, name, email, role, accountIds, actorId);

        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "email", updated.getEmail()
        ));
    }

    // Only the getUserById method needs updating in your UserController.java
// Replace the existing getUserById method with this:

    /**
     * Admin & ReadOnly: get user by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY')")
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                log.warn("User not found: {}", id);
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            List<AccountDto> accounts = userService.getUserAccounts(id);

            // Return in format frontend expects
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(
                            "id", user.getId(),
                            "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                            "lastName", user.getLastName() != null ? user.getLastName() : "",
                            "name", user.getName() != null ? user.getName() : "",
                            "email", user.getEmail(),
                            "role", user.getRole().name(),
                            "accounts", accounts.stream()
                                    .map(AccountDto::getId)
                                    .collect(java.util.stream.Collectors.toList())
                    )
            ));
        } catch (Exception e) {
            log.error("Error fetching user by ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal Server Error"));
        }
    }

    /**
     * Get user's assigned accounts
     */
    @GetMapping("/{userId}/accounts")
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY')")
    public ResponseEntity<?> getUserAccounts(@PathVariable String userId) {
        var accounts = userService.getUserAccounts(userId);
        log.info("Fetching accounts for user: {}", userId);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Admin: assign accounts
     */
    @PostMapping("/{userId}/assign-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assignAccounts(
            @PathVariable String userId,
            @RequestBody Map<String, List<String>> body
    ) {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        List<String> accountIds = body.get("accountIds");
        String actorId = getActorId();

        log.info("Admin {} assigning accounts to user {}: {}",
                actorId, userId, accountIds);

        userService.assignAccounts(userId, accountIds, actorId);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Bulk CSV upload
     */
    @PostMapping("/bulk-upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> bulkUpload(@RequestParam("file") MultipartFile file)
            throws Exception {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "CSV required"));
        }

        String actorId = getActorId();
        log.info("Admin {} uploading CSV: {} ({} bytes)",
                actorId, file.getOriginalFilename(), file.getSize());

        String jobId = userService.enqueueCsvBulk(file.getBytes(), actorId);

        return ResponseEntity.ok(Map.of("ok", true, "jobId", jobId));
    }

    /**
     * Get CSV job status
     */
    @GetMapping("/bulk-upload/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCsvJobStatus(@PathVariable String jobId) {
        var jobOpt = userService.getCsvJobStatus(jobId);

        if (jobOpt.isEmpty()) {
            log.warn("CSV job not found: {}", jobId);
            return ResponseEntity.notFound().build();
        }

        var job = jobOpt.get();

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "successCount", job.getSuccessCount() != null ? job.getSuccessCount() : 0,
                "failureCount", job.getFailureCount() != null ? job.getFailureCount() : 0,
                "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : "",
                "createdAt", job.getCreatedAt().toString(),
                "updatedAt", job.getUpdatedAt().toString()
        ));
    }
}