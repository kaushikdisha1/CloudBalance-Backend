package com.example.cloudBalanceBackend.service;

import com.example.cloudBalanceBackend.exception.AccountNotAssignedException;
import com.example.cloudBalanceBackend.exception.AccountRequiredException;
import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.repository.UserAccountRepository;
import com.example.cloudBalanceBackend.service.aws.AwsService;
import com.example.cloudBalanceBackend.service.snowflake.SnowflakeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DashboardService {

    private final SnowflakeService snowflakeService;
    private final AwsService awsService;
    private final UserAccountRepository uaRepo;

    public DashboardService(SnowflakeService snowflakeService, AwsService awsService, UserAccountRepository uaRepo) {
        this.snowflakeService = snowflakeService;
        this.awsService = awsService;
        this.uaRepo = uaRepo;
    }

    public Map<String,Object> getCostExplorer(Authentication auth, String accountId) {
        Role role = getRole(auth);
        String userId = auth.getName();

        if (role == Role.CUSTOMER) {
            if (accountId == null) {
                throw new AccountRequiredException("accountId required for CUSTOMER");
            }
            boolean assigned = uaRepo.findByUserIdAndAccountId(userId, accountId).isPresent();
            if (!assigned) {
                throw new AccountNotAssignedException("Account not assigned to user");
            }
            // Customer → only assigned account
            return snowflakeService.getCostData(accountId);
        }

        // Admin/ReadOnly → all accounts or specific account
        if (accountId != null) {
            return snowflakeService.getCostData(accountId);
        } else {
            return snowflakeService.getCostDataAllAccounts();
        }
    }

    public Map<String,Object> getAwsServices(Authentication auth, String accountId) {
        Role role = getRole(auth);
        String userId = auth.getName();

        if (role == Role.CUSTOMER) {
            if (accountId == null) {
                throw new AccountRequiredException("accountId required for CUSTOMER");
            }
            boolean assigned = uaRepo.findByUserIdAndAccountId(userId, accountId).isPresent();
            if (!assigned) {
                throw new AccountNotAssignedException("Account not assigned to user");
            }
            return awsService.getServiceData(accountId);
        }

        // Admin/ReadOnly → all accounts or specific account
        if (accountId != null) {
            return awsService.getServiceData(accountId);
        } else {
            return awsService.getServiceDataAllAccounts();
        }
    }

    private Role getRole(Authentication auth) {
        return Role.valueOf(
                auth.getAuthorities().stream()
                        .findFirst()
                        .orElseThrow()
                        .getAuthority()
                        .replace("ROLE_", "")
        );
    }
}
