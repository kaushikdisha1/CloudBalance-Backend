package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.dto.CreateAccountRequest;
import com.example.cloudBalanceBackend.exception.AccountException;
import com.example.cloudBalanceBackend.model.*;
import com.example.cloudBalanceBackend.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogger auditLogger;

    public AccountService(AccountRepository accountRepo, UserAccountRepository uaRepo, AuditLogger auditLogger) {
        this.accountRepo = accountRepo;
        this.uaRepo = uaRepo;
        this.auditLogger = auditLogger;
    }

    public Account createAccount(CreateAccountRequest req) {
        Account a = Account.builder()
                .id(UUID.randomUUID().toString())
                .name(req.getName())
                .provider(req.getProvider())
                .providerAccountId(req.getProviderAccountId())
                .meta(req.getMeta())
                .status("ORPHAN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Account saved = accountRepo.save(a);
        auditLogger.log("CREATE_ACCOUNT", "system",
                "Created account: " + saved.getId() + " provider=" + saved.getProvider());
        return saved;
    }

    public boolean hasRole(Authentication auth, Role role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    public List<AccountDto> listAccounts(Authentication auth) {
        try {
            if (hasRole(auth, Role.ADMIN) || hasRole(auth, Role.READ_ONLY)) {
                return accountRepo.findAll().stream().map(AccountDto::new).toList();
            } else {
                String userId = auth.getName();
                return uaRepo.findByUserId(userId).stream()
                        .map(UserAccount::getAccount)
                        .map(AccountDto::new)
                        .toList();
            }
        } catch (Exception e) {
            throw new AccountException("Failed to list accounts", e);
        }
    }
}
