package com.example.cloudBalanceBackend.amqp;

import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import com.example.cloudBalanceBackend.utils.ValidationUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@SuppressWarnings("unchecked")
public class UserCreationListener {

    private final UserRepository userRepo;
    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogRepository auditRepo;
    private final BCryptPasswordEncoder encoder;

    public UserCreationListener(UserRepository userRepo, AccountRepository accountRepo, UserAccountRepository uaRepo, AuditLogRepository auditRepo, BCryptPasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.accountRepo = accountRepo;
        this.uaRepo = uaRepo;
        this.auditRepo = auditRepo;
        this.encoder = encoder;
    }

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
            if (!"create_user".equals(payload.get("type"))) return;
            Map<String,Object> user = (Map<String,Object>) payload.get("user");
            String actorId = (String) payload.get("actorId");
            String email = (String) user.get("email");
            String password = (String) user.get("password");

            ValidationUtils.validateEmail(email);
            ValidationUtils.validatePassword(password);

            if (userRepo.existsByEmail(email)) {
                System.out.println("Skipping duplicate user " + email);
                return;
            }
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
            List<String> aids = (List<String>) user.getOrDefault("accountIds", List.of());
            for (String aid : aids) {
                accountRepo.findById(aid).ifPresent(acc -> {
                    UserAccount ua = UserAccount.builder().id(UUID.randomUUID().toString()).user(u).account(acc).assignedAt(Instant.now()).build();
                    uaRepo.save(ua);
                });
            }
            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID().toString())
                    .actorId(actorId)
                    .action("ASYNC_CREATE_USER")
                    .details("created user " + email).createdAt(Instant.now()).build();
            auditRepo.save(log);
            System.out.println("Created user from MQ: " + email);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}