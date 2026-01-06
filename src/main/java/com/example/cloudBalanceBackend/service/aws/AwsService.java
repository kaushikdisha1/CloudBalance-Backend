package com.example.cloudBalanceBackend.service.aws;

import com.example.cloudBalanceBackend.exception.AwsServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class AwsService {

    @Value("${aws.assume-role.enabled:false}")
    private boolean assumeRoleEnabled;

    @Value("${aws.assume-role.role-arn-template:}")
    private String roleArnTemplate;

    @Value("${aws.region:us-east-1}")
    private String defaultRegion;

    private boolean isAwsConfigured() {
        boolean configured = assumeRoleEnabled && roleArnTemplate != null && !roleArnTemplate.isEmpty();
        log.debug("AWS AssumeRole configured: {}", configured);
        return configured;
    }

    // Get AWS credentials for specific account using AssumeRole
    private AwsSessionCredentials getAccountCredentials(String accountId) {
        if (!isAwsConfigured()) {
            log.debug("AWS AssumeRole not configured for account: {}", accountId);
            return null;
        }

        log.info("Assuming role for account: {}", accountId);

        try (StsClient stsClient = StsClient.builder()
                .region(Region.of(defaultRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            String roleArn = roleArnTemplate.replace("{accountId}", accountId);
            String sessionName = "cloudbalance-session-" + System.currentTimeMillis();

            log.debug("AssumeRole ARN: {}, SessionName: {}", roleArn, sessionName);

            AssumeRoleRequest roleRequest = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName(sessionName)
                    .durationSeconds(3600)
                    .build();

            AssumeRoleResponse roleResponse = stsClient.assumeRole(roleRequest);
            Credentials credentials = roleResponse.credentials();

            log.info("Successfully assumed role for account: {}", accountId);

            return AwsSessionCredentials.create(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken()
            );
        } catch (Exception e) {
            log.error("Failed to assume role for account {}: {}", accountId, e.getMessage(), e);
            throw new AwsServiceException("Failed to assume role for account " + accountId, e);
        }
    }

    public Map<String, Object> getServiceData(String accountId) {
        log.info("Fetching AWS service data for account: {}", accountId);

        if (!isAwsConfigured()) {
            log.info("AWS not configured, returning dummy data for account: {}", accountId);
            return getDummyServiceData(accountId);
        }

        try {
            AwsSessionCredentials credentials = getAccountCredentials(accountId);
            if (credentials == null) {
                log.warn("No credentials obtained, returning dummy data");
                return getDummyServiceData(accountId);
            }

            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);
            Region region = Region.of(defaultRegion);

            // Fetch EC2 data
            Map<String, Object> ec2Data = getEc2Data(credentialsProvider, region);

            // Fetch S3 data
            Map<String, Object> s3Data = getS3Data(credentialsProvider, region);

            // Fetch Lambda data
            Map<String, Object> lambdaData = getLambdaData(credentialsProvider, region);

            log.info("Successfully fetched AWS data for account: {}", accountId);

            return Map.of(
                    "meta", Map.of(
                            "accountId", accountId,
                            "fetchedAt", Instant.now().toString()
                    ),
                    "services", List.of(ec2Data, s3Data, lambdaData)
            );

        } catch (Exception e) {
            log.error("Failed to fetch AWS service data for account {}: {}",
                    accountId, e.getMessage(), e);
            throw new AwsServiceException("Failed to fetch AWS service data for account " + accountId, e);
        }
    }

    public Map<String, Object> getServiceDataAllAccounts() {
        log.info("Fetching AWS service data for all accounts");

        if (!isAwsConfigured()) {
            log.info("AWS not configured, returning dummy data for all accounts");
            return getDummyServiceDataAllAccounts();
        }

        try {
            // For "all accounts", use default credentials
            Region region = Region.of(defaultRegion);
            DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();

            Map<String, Object> ec2Data = getEc2DataDefault(credentialsProvider, region);
            Map<String, Object> s3Data = getS3DataDefault(credentialsProvider, region);
            Map<String, Object> lambdaData = getLambdaDataDefault(credentialsProvider, region);

            log.info("Successfully fetched AWS data for all accounts");

            return Map.of(
                    "meta", Map.of(
                            "accountId", "all",
                            "fetchedAt", Instant.now().toString()
                    ),
                    "services", List.of(ec2Data, s3Data, lambdaData)
            );

        } catch (Exception e) {
            log.error("Failed to fetch AWS service data for all accounts: {}", e.getMessage(), e);
            throw new AwsServiceException("Failed to fetch AWS service data for all accounts", e);
        }
    }

    // EC2 data with assumed role credentials
    private Map<String, Object> getEc2Data(StaticCredentialsProvider credentials, Region region) {
        log.debug("Fetching EC2 data for region: {}", region.id());

        try (Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            DescribeInstancesResponse response = ec2.describeInstances();
            int runningInstances = 0;
            int stoppedInstances = 0;

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    if (instance.state().name() == InstanceStateName.RUNNING) {
                        runningInstances++;
                    } else if (instance.state().name() == InstanceStateName.STOPPED) {
                        stoppedInstances++;
                    }
                }
            }

            log.info("EC2 data: running={}, stopped={}", runningInstances, stoppedInstances);

            return Map.of(
                    "name", "EC2",
                    "region", region.id(),
                    "metrics", Map.of(
                            "runningInstances", runningInstances,
                            "stoppedInstances", stoppedInstances
                    )
            );
        }
    }

    // EC2 data with default credentials
    private Map<String, Object> getEc2DataDefault(DefaultCredentialsProvider credentials, Region region) {
        log.debug("Fetching EC2 data (default credentials) for region: {}", region.id());

        try (Ec2Client ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            DescribeInstancesResponse response = ec2.describeInstances();
            int runningInstances = 0;

            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    if (instance.state().name() == InstanceStateName.RUNNING) {
                        runningInstances++;
                    }
                }
            }

            log.info("EC2 data (all accounts): running={}", runningInstances);

            return Map.of(
                    "name", "EC2",
                    "region", region.id(),
                    "metrics", Map.of("runningInstances", runningInstances)
            );
        }
    }

    // S3 data with assumed role credentials
    private Map<String, Object> getS3Data(StaticCredentialsProvider credentials, Region region) {
        log.debug("Fetching S3 data for region: {}", region.id());

        try (S3Client s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            ListBucketsResponse response = s3.listBuckets();
            int bucketCount = response.buckets().size();

            log.info("S3 data: buckets={}", bucketCount);

            return Map.of(
                    "name", "S3",
                    "region", region.id(),
                    "metrics", Map.of("buckets", bucketCount)
            );
        }
    }

    // S3 data with default credentials
    private Map<String, Object> getS3DataDefault(DefaultCredentialsProvider credentials, Region region) {
        log.debug("Fetching S3 data (default credentials) for region: {}", region.id());

        try (S3Client s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            ListBucketsResponse response = s3.listBuckets();
            int bucketCount = response.buckets().size();

            log.info("S3 data (all accounts): buckets={}", bucketCount);

            return Map.of(
                    "name", "S3",
                    "region", region.id(),
                    "metrics", Map.of("buckets", bucketCount)
            );
        }
    }

    // Lambda data with assumed role credentials
    private Map<String, Object> getLambdaData(StaticCredentialsProvider credentials, Region region) {
        log.debug("Fetching Lambda data for region: {}", region.id());

        try (LambdaClient lambda = LambdaClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            ListFunctionsResponse response = lambda.listFunctions();
            int functionCount = response.functions().size();

            log.info("Lambda data: functions={}", functionCount);

            return Map.of(
                    "name", "Lambda",
                    "region", region.id(),
                    "metrics", Map.of("functions", functionCount)
            );
        }
    }

    // Lambda data with default credentials
    private Map<String, Object> getLambdaDataDefault(DefaultCredentialsProvider credentials, Region region) {
        log.debug("Fetching Lambda data (default credentials) for region: {}", region.id());

        try (LambdaClient lambda = LambdaClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build()) {

            ListFunctionsResponse response = lambda.listFunctions();
            int functionCount = response.functions().size();

            log.info("Lambda data (all accounts): functions={}", functionCount);

            return Map.of(
                    "name", "Lambda",
                    "region", region.id(),
                    "metrics", Map.of("functions", functionCount)
            );
        }
    }

    // Fallback dummy data
    private Map<String, Object> getDummyServiceData(String accountId) {
        log.debug("Generating dummy AWS service data for account: {}", accountId);
        return Map.of(
                "meta", Map.of(
                        "accountId", accountId == null ? "all" : accountId,
                        "fetchedAt", Instant.now().toString()
                ),
                "services", List.of(
                        Map.of("name", "EC2", "region", "us-east-1",
                                "metrics", Map.of("runningInstances", 3, "stoppedInstances", 2)),
                        Map.of("name", "S3", "region", "us-east-1",
                                "metrics", Map.of("buckets", 5, "totalSizeGB", 42)),
                        Map.of("name", "Lambda", "region", "us-east-1",
                                "metrics", Map.of("functions", 8))
                )
        );
    }

    private Map<String, Object> getDummyServiceDataAllAccounts() {
        log.debug("Generating dummy AWS service data for all accounts");
        return Map.of(
                "meta", Map.of(
                        "accountId", "all",
                        "fetchedAt", Instant.now().toString()
                ),
                "services", List.of(
                        Map.of("name", "Lambda", "region", "us-east-1",
                                "metrics", Map.of("functions", 12)),
                        Map.of("name", "DynamoDB", "region", "us-east-1",
                                "metrics", Map.of("tables", 7)),
                        Map.of("name", "EC2", "region", "us-east-1",
                                "metrics", Map.of("runningInstances", 15))
                )
        );
    }
}