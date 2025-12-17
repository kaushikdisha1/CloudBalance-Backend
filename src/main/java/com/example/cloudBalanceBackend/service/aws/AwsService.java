package com.example.cloudBalanceBackend.service.aws;

import com.example.cloudBalanceBackend.exception.AwsServiceException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AwsService {

    public Map<String, Object> getServiceData(String accountId) {
        try {
            // TODO: implement real AWS SDK assume-role; currently stubbed
            return Map.of(
                    "meta", Map.of(
                            "accountId", accountId == null ? "all" : accountId,
                            "fetchedAt", Instant.now().toString()
                    ),
                    "services", java.util.List.of(
                            Map.of("name", "EC2", "region", "us-east-1",
                                    "metrics", Map.of("runningInstances", 3)),
                            Map.of("name", "S3", "region", "us-east-1",
                                    "metrics", Map.of("buckets", 5, "totalSizeGB", 42))
                    )
            );
        } catch (Exception e) {
            // Wrap any failure in a custom exception
            throw new AwsServiceException("Failed to fetch AWS service data", e);
        }
    }

    public Map<String, Object> getServiceDataAllAccounts() {
        try {
            // TODO: implement real AWS SDK logic for all accounts
            return Map.of(
                    "meta", Map.of(
                            "accountId", "all",
                            "fetchedAt", Instant.now().toString()
                    ),
                    "services", java.util.List.of(
                            Map.of("name", "Lambda", "region", "us-east-1",
                                    "metrics", Map.of("functions", 12)),
                            Map.of("name", "DynamoDB", "region", "us-east-1",
                                    "metrics", Map.of("tables", 7))
                    )
            );
        } catch (Exception e) {
            throw new AwsServiceException("Failed to fetch AWS service data for all accounts", e);
        }
    }
}
