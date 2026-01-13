package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.CostExplorerRequest;
import com.example.cloudBalanceBackend.service.DashboardService;
import com.example.cloudBalanceBackend.service.snowflake.SnowflakeService;
import com.example.cloudBalanceBackend.utils.CostDataTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final SnowflakeService snowflakeService;
    private final CostDataTransformer transformer;

    @PostMapping("/cost-explorer")
    public ResponseEntity<Map<String, Object>> getCostExplorer(
            @RequestBody CostExplorerRequest request,
            Authentication auth) {

        log.info("Received cost explorer request: groupBy={}, startDate={}, endDate={}",
                request.getGroupBy(), request.getStartDate(), request.getEndDate());

        try {
            if (request.getGroupBy() == null || request.getStartDate() == null || request.getEndDate() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "groupBy, startDate, and endDate are required"));
            }

            List<Map<String, Object>> costData = dashboardService.getCostExplorer(
                    auth,
                    request.getAccountId(),
                    request.getGroupBy(),
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFilters() != null ? request.getFilters() : new HashMap<>()
            );

            log.info("Fetched {} rows from Snowflake", costData.size());

            Map<String, Object> response = transformer.transform(costData, request.getGroupBy());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing cost explorer request", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/cost-explorer/test")
    public ResponseEntity<?> testSnowflakeConnection() {
        log.info("Testing Snowflake connection...");

        try {
            List<Map<String, Object>> testData = snowflakeService.testConnection();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Snowflake connection successful",
                    "sampleData", testData
            ));
        } catch (Exception e) {
            log.error("Snowflake connection test failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    @GetMapping("/cost-explorer/simple-test")
    public ResponseEntity<?> simpleTest() {
        log.info("Simple test - fetching data from Snowflake");

        try {
            List<Map<String, Object>> data = snowflakeService.getCostData(
                    "service",
                    LocalDate.of(2024, 10, 1),
                    LocalDate.of(2025, 3, 31),
                    new HashMap<>()
            );

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "rowCount", data.size(),
                    "data", data
            ));
        } catch (Exception e) {
            log.error("Simple test failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/aws-services")
    public ResponseEntity<Map<String, Object>> awsServices(
            @RequestParam(required = false) String accountId,
            Authentication auth) {

        return ResponseEntity.ok(
                dashboardService.getAwsServices(auth, accountId)
        );
    }
}