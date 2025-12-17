package com.example.cloudBalanceBackend.service.snowflake;

import com.example.cloudBalanceBackend.exception.SnowflakeException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SnowflakeService {

    public Map<String,Object> getCostData(String accountId) {
        try {
            // TODO: replace with actual Snowflake JDBC queries
            return Map.of(
                    "accountId", accountId == null ? "all" : accountId,
                    "totals", Map.of("costUSD", 1234.56, "lastMonth", 980.12),
                    "byService", java.util.List.of(Map.of("service","EC2","costUSD",400.1)),
                    "trend", java.util.List.of(Map.of("date","2025-11-01","cost",30))
            );
        } catch (Exception e) {
            // Wrap any failure in a custom exception
            throw new SnowflakeException("Failed to fetch cost data from Snowflake", e);
        }
    }

    public Map<String,Object> getCostDataAllAccounts() {
        try {
            // TODO: replace with actual Snowflake JDBC queries for all accounts
            return Map.of(
                    "accountId", "all",
                    "totals", Map.of("costUSD", 5678.90, "lastMonth", 4321.00),
                    "byService", java.util.List.of(Map.of("service","S3","costUSD",1200.5)),
                    "trend", java.util.List.of(Map.of("date","2025-11-01","cost",50))
            );
        } catch (Exception e) {
            throw new SnowflakeException("Failed to fetch cost data for all accounts", e);
        }
    }
}
