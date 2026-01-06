package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.exception.BadRequestException;
import com.example.cloudBalanceBackend.exception.OnboardingException;
import com.example.cloudBalanceBackend.model.Account;
import com.example.cloudBalanceBackend.repository.AccountRepository;
import com.example.cloudBalanceBackend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final AccountRepository accountRepo;
    private final UserAccountRepository uaRepo;
    private final AuditLogger auditLogger;

    public List<AccountDto> onboardAccounts(List<String> accountIds, String actorId) {
        log.info("Onboarding accounts: accountIds={}, actor={}", accountIds, actorId);

        if (accountIds == null || accountIds.isEmpty()) {
            log.error("No account IDs provided for onboarding");
            throw new BadRequestException("At least one accountId must be provided");
        }

        try {
            List<Account> created = accountIds.stream()
                    .map(id -> {
                        Account existing = accountRepo.findById(id).orElse(null);
                        if (existing != null) {
                            log.debug("Account {} already exists, returning existing", id);
                            return existing;
                        } else {
                            Account newAccount = accountRepo.save(new Account(id));
                            log.info("Onboarded new account: {}", id);
                            return newAccount;
                        }
                    })
                    .collect(Collectors.toList());

            auditLogger.log("ONBOARD_ACCOUNTS", actorId,
                    "Onboarded accounts: " + accountIds);

            log.info("Successfully onboarded {} accounts", created.size());

            return created.stream()
                    .map(AccountDto::new)  //new accountdto()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to onboard accounts: {}", e.getMessage(), e);
            throw new OnboardingException("Failed to onboard accounts", e);
        }
    }

    public List<AccountDto> getOrphanAccounts() {
        log.info("Fetching orphan accounts");

        try {
            List<AccountDto> orphans = accountRepo.findAll().stream()  //can solve using left join
                    .filter(acc -> {
                        boolean isOrphan = uaRepo.findByAccountId(acc.getId()).isEmpty();
                        if (isOrphan) {
                            log.debug("Account {} is orphan", acc.getId());
                        }
                        return isOrphan;
                    })
                    .map(AccountDto::new)
                    .collect(Collectors.toList());

            log.info("Found {} orphan accounts", orphans.size());
            return orphans;
        } catch (Exception e) {
            log.error("Failed to fetch orphan accounts: {}", e.getMessage(), e);
            throw new OnboardingException("Failed to fetch orphan accounts", e);
        }
    }
}