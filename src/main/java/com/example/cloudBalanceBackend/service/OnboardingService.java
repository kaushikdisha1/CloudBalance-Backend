package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.exception.BadRequestException;
import com.example.cloudBalanceBackend.exception.OnboardingException;
import com.example.cloudBalanceBackend.model.Account;
import com.example.cloudBalanceBackend.repository.AccountRepository;
import com.example.cloudBalanceBackend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogger auditLogger;

    public List<AccountDto> onboardAccounts(List<String> accountIds, String actorId) {
        if (accountIds == null || accountIds.isEmpty()) {
            throw new BadRequestException("At least one accountId must be provided");
        }
        try {
            List<Account> created = accountIds.stream()
                    .map(id -> accountRepo.findById(id)
                            .orElseGet(() -> accountRepo.save(new Account(id))))
                    .collect(Collectors.toList());

            auditLogger.log("ONBOARD_ACCOUNTS", actorId,
                    "Onboarded accounts: " + accountIds);

            return created.stream().map(AccountDto::new).collect(Collectors.toList());
        } catch (Exception e) {
            throw new OnboardingException("Failed to onboard accounts", e);
        }
    }

    public List<AccountDto> getOrphanAccounts() {
        try {
            return accountRepo.findAll().stream()
                    .filter(acc -> uaRepo.findByAccountId(acc.getId()).isEmpty())
                    .map(AccountDto::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new OnboardingException("Failed to fetch orphan accounts", e);
        }
    }
}
