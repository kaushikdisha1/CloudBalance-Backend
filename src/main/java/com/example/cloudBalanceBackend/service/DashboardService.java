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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SnowflakeService snowflakeService;
    private final AwsService awsService;
    private final UserAccountRepository uaRepo;

    public List<Map<String, Object>> getCostExplorer(
            Authentication auth,
            String accountId,
            String groupBy,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, List<String>> filters
    ) {
        log.info("DashboardService: Getting cost data for groupBy={}", groupBy);

        // Initialize filters if null
        if (filters == null) {
            filters = new HashMap<>();
        }

        // Handle authentication and authorization
        if (auth != null) {
            Role role = getRole(auth);
            String userId = auth.getName();

            if (role == Role.CUSTOMER) {

                var accounts =uaRepo.findByUserId(userId);

                if (accounts.isEmpty()) {
                    throw new AccountNotAssignedException("Account not assigned to user");
                }
                accountId = accounts.get(0).getAccount().getId();
            }

            // Add accountId filter if provided
            if (accountId != null) {
                filters.put("accountId", List.of(accountId));
            }
        }

        // Fetch data from Snowflake - SNOWFLAKE IS QUERIED
        log.info("Calling Snowflake service to fetch data...");
        List<Map<String, Object>> result = snowflakeService.getCostData(
                groupBy,
                startDate,
                endDate,
                filters
        );

        log.info("Received {} rows from Snowflake", result.size());
        return result;
    }

    public Map<String, Object> getAwsServices(Authentication auth, String accountId) {
        Role role = getRole(auth);
        String userId = auth.getName();

        if (role == Role.CUSTOMER) {
            if (accountId == null) {
                throw new AccountRequiredException("accountId required for CUSTOMER");
            }

            boolean assigned = uaRepo
                    .findByUserIdAndAccountId(userId, accountId)
                    .isPresent();

            if (!assigned) {
                throw new AccountNotAssignedException("Account not assigned to user");
            }

            return awsService.getServiceData(accountId);
        }

        return (accountId != null)
                ? awsService.getServiceData(accountId)
                : awsService.getServiceDataAllAccounts();
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
