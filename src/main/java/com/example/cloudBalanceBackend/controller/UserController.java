package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Admin & ReadOnly: list users with pagination
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY')")
    public ResponseEntity<?> listUsers(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        if (!userService.hasAnyRole(List.of(Role.ADMIN, Role.READ_ONLY))) {
            return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        }
        var pageable = PageRequest.of(page, size);
        var users = userService.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    // Admin: create user
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        }
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String roleStr = (String) body.get("role");
        List<String> accountIds = (List<String>) body.getOrDefault("accountIds", List.of());
        Role role = Role.valueOf(roleStr);
        var actorId = (String) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var u = userService.createUserSync(name, email, password, role, accountIds, actorId);
        return ResponseEntity.status(201).body(Map.of("id", u.getId(), "email", u.getEmail()));
    }

    // Admin: assign accounts
    @PostMapping("/{userId}/assign-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assignAccounts(@PathVariable String userId,
                                            @RequestBody Map<String, List<String>> body) {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        }
        List<String> accountIds = body.get("accountIds");
        var actorId = (String) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        userService.assignAccounts(userId, accountIds, actorId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // Bulk CSV upload
    @PostMapping("/bulk-upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> bulkUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if (!userService.hasRole(Role.ADMIN)) {
            return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error","CSV required"));
        }
        var actorId = (String) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        String jobId = userService.enqueueCsvBulk(file.getBytes(), actorId);
        return ResponseEntity.ok(Map.of("ok", true, "jobId", jobId));
    }
}
