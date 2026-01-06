package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.dto.CreateAccountRequest;
import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogger auditLogger;

    /**
     * Create a new cloud account (Admin only)
     */
    public Account createAccount(CreateAccountRequest req, String actorId) {
        log.info("Creating account: name={}, provider={}", req.getName(), req.getProvider());

        Account a = Account.builder()
                .id(UUID.randomUUID().toString())
                .name(req.getName())  //Prod AWS Account
                .provider(req.getProvider())
                .providerAccountId(req.getProviderAccountId())
                .meta(req.getMeta()) //Region, owner, env
                .status("ORPHAN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Account saved = accountRepo.save(a);
        log.info("Created account: id={}, status=ORPHAN", saved.getId());

        auditLogger.log(
                "CREATE_ACCOUNT",
                actorId,
                "Created account " + saved.getId() + " (provider: " + saved.getProvider() + ")"
        );

        return saved;
    }

    /**
     * List visible accounts based on user role
     * - ADMIN and READ_ONLY: Can see ALL accounts
     * - CUSTOMER: Can only see assigned accounts
     */
    public List<AccountDto> listVisibleAccounts(User user) {
        log.debug("Listing visible accounts for user: {} with role: {}", user.getEmail(), user.getRole());

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.READ_ONLY) {
            // Admin and Read-Only can see ALL accounts
            List<AccountDto> allAccounts = accountRepo.findAll()
                    .stream()
                    .map(AccountDto::new)
                    .collect(Collectors.toList());
            log.info("User {} (role: {}) can see all {} accounts",
                    user.getEmail(), user.getRole(), allAccounts.size());
            return allAccounts;
        }

        // CUSTOMER → assigned accounts only
        List<AccountDto> assignedAccounts = uaRepo.findByUserId(user.getId())
                .stream()
                .map(UserAccount::getAccount)
                .map(AccountDto::new)
                .collect(Collectors.toList());
        log.info("User {} (role: CUSTOMER) can see {} assigned accounts",
                user.getEmail(), assignedAccounts.size());
        return assignedAccounts;
    }
}