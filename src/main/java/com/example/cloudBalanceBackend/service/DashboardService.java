package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.exception.AccountNotAssignedException;
import com.example.cloudBalanceBackend.exception.AccountRequiredException;
import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.repository.UserAccountRepository;
import com.example.cloudBalanceBackend.service.aws.AwsService;
import com.example.cloudBalanceBackend.service.snowflake.SnowflakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SnowflakeService snowflakeService;
    private final AwsService awsService;
    private final UserAccountRepository uaRepo;

    public Map<String, Object> getCostExplorer(Authentication auth, String accountId) {
        Role role = getRole(auth);
        String userId = auth.getName();

        log.info("Cost Explorer request: user={}, role={}, accountId={}", userId, role, accountId);

        if (role == Role.CUSTOMER) {
            // CUSTOMER must provide accountId
            if (accountId == null) {
                log.error("CUSTOMER {} did not provide accountId", userId);
                throw new AccountRequiredException("accountId required for CUSTOMER");
            }

            // Verify account is assigned to this customer
            boolean assigned = uaRepo.findByUserIdAndAccountId(userId, accountId).isPresent();
            if (!assigned) {
                log.error("CUSTOMER {} attempted to access unassigned account: {}", userId, accountId);
                throw new AccountNotAssignedException("Account not assigned to user");
            }

            log.info("CUSTOMER {} accessing assigned account: {}", userId, accountId);
            return snowflakeService.getCostData(accountId);
        }

        // Admin/ReadOnly → all accounts or specific account
        if (accountId != null) {
            log.info("{} accessing specific account: {}", role, accountId);
            return snowflakeService.getCostData(accountId);
        } else {
            log.info("{} accessing all accounts", role);
            return snowflakeService.getCostDataAllAccounts();
        }
    }

    public Map<String, Object> getAwsServices(Authentication auth, String accountId) {
        Role role = getRole(auth);
        String userId = auth.getName();

        log.info("AWS Services request: user={}, role={}, accountId={}", userId, role, accountId);

        if (role == Role.CUSTOMER) {
            // CUSTOMER must provide accountId
            if (accountId == null) {
                log.error("CUSTOMER {} did not provide accountId", userId);
                throw new AccountRequiredException("accountId required for CUSTOMER");
            }

            // Verify account is assigned to this customer
            boolean assigned = uaRepo.findByUserIdAndAccountId(userId, accountId).isPresent();
            if (!assigned) {
                log.error("CUSTOMER {} attempted to access unassigned account: {}", userId, accountId);
                throw new AccountNotAssignedException("Account not assigned to user");
            }

            log.info("CUSTOMER {} accessing assigned account: {}", userId, accountId);
            return awsService.getServiceData(accountId);
        }

        // Admin/ReadOnly → all accounts or specific account
        if (accountId != null) {
            log.info("{} accessing specific account: {}", role, accountId);
            return awsService.getServiceData(accountId);
        } else {
            log.info("{} accessing all accounts", role);
            return awsService.getServiceDataAllAccounts();
        }
    }

    private Role getRole(Authentication auth) {
        String roleStr = auth.getAuthorities().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role found in authentication"))
                .getAuthority()
                .replace("ROLE_", "");
        return Role.valueOf(roleStr);
    }
}