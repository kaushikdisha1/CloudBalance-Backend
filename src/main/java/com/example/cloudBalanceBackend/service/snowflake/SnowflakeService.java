package com.example.cloudBalanceBackend.service.snowflake;

import com.example.cloudBalanceBackend.exception.SnowflakeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class SnowflakeService {

    @Value("${snowflake.url:}")
    private String snowflakeUrl;

    @Value("${snowflake.user:}")
    private String snowflakeUser;

    @Value("${snowflake.password:}")
    private String snowflakePassword;

    @Value("${snowflake.database:}")
    private String snowflakeDatabase;

    @Value("${snowflake.schema:}")
    private String snowflakeSchema;

    @Value("${snowflake.warehouse:}")
    private String snowflakeWarehouse;

    private boolean isConfigured() {
        boolean configured = snowflakeUrl != null && !snowflakeUrl.isEmpty()
                && snowflakeUser != null && !snowflakeUser.isEmpty();
        log.debug("Snowflake configured: {}", configured);
        return configured;
    }

    private Connection getConnection() throws SQLException {
        if (!isConfigured()) {
            log.warn("Snowflake not configured, connection cannot be established");
            throw new SnowflakeException("Snowflake not configured", null);
        }

        log.debug("Creating Snowflake connection: url={}, database={}, schema={}",
                snowflakeUrl, snowflakeDatabase, snowflakeSchema);

        Properties props = new Properties();
        props.put("user", snowflakeUser);
        props.put("password", snowflakePassword);
        props.put("db", snowflakeDatabase);
        props.put("schema", snowflakeSchema);
        props.put("warehouse", snowflakeWarehouse);

        return DriverManager.getConnection(snowflakeUrl, props);
    }

    public Map<String, Object> getCostData(String accountId) {
        log.info("Fetching cost data for account: {}", accountId);

        // If Snowflake not configured, return dummy data
        if (!isConfigured()) {
            log.info("Snowflake not configured, returning dummy data for account: {}", accountId);
            return getDummyCostData(accountId);
        }

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT " +
                             "  SUM(cost_usd) as total_cost, " +
                             "  service_name, " +
                             "  DATE_TRUNC('day', usage_date) as date " +
                             "FROM cloud_costs " +
                             "WHERE account_id = ? " +
                             "  AND usage_date >= DATEADD(month, -1, CURRENT_DATE()) " +
                             "GROUP BY service_name, DATE_TRUNC('day', usage_date) " +
                             "ORDER BY date DESC"
             )) {

            log.debug("Executing Snowflake query for account: {}", accountId);
            stmt.setString(1, accountId);
            ResultSet rs = stmt.executeQuery();

            double totalCost = 0;
            Map<String, Double> byService = new HashMap<>();
            List<Map<String, Object>> trend = new ArrayList<>();

            while (rs.next()) {
                double cost = rs.getDouble("total_cost");
                String service = rs.getString("service_name");
                Date date = rs.getDate("date");

                totalCost += cost;
                byService.merge(service, cost, Double::sum);
                trend.add(Map.of("date", date.toString(), "cost", cost));
            }

            List<Map<String, Object>> serviceList = byService.entrySet().stream()
                    .map(e -> Map.of("service", (Object) e.getKey(), "costUSD", (Object) e.getValue()))
                    .toList();

            log.info("Fetched cost data for account {}: totalCost={}, services={}",
                    accountId, totalCost, byService.size());

            return Map.of(
                    "accountId", accountId,
                    "totals", Map.of("costUSD", totalCost, "lastMonth", totalCost * 0.8),
                    "byService", serviceList,
                    "trend", trend
            );

        } catch (SQLException e) {
            log.error("Failed to fetch cost data from Snowflake for account {}: {}",
                    accountId, e.getMessage(), e);
            throw new SnowflakeException("Failed to fetch cost data from Snowflake", e);
        }
    }

    public Map<String, Object> getCostDataAllAccounts() {
        log.info("Fetching cost data for all accounts");

        if (!isConfigured()) {
            log.info("Snowflake not configured, returning dummy data for all accounts");
            return getDummyCostDataAllAccounts();
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT " +
                             "  SUM(cost_usd) as total_cost, " +
                             "  service_name, " +
                             "  DATE_TRUNC('day', usage_date) as date " +
                             "FROM cloud_costs " +
                             "WHERE usage_date >= DATEADD(month, -1, CURRENT_DATE()) " +
                             "GROUP BY service_name, DATE_TRUNC('day', usage_date) " +
                             "ORDER BY date DESC"
             )) {

            log.debug("Executing Snowflake query for all accounts");

            double totalCost = 0;
            Map<String, Double> byService = new HashMap<>();
            List<Map<String, Object>> trend = new ArrayList<>();

            while (rs.next()) {
                double cost = rs.getDouble("total_cost");
                String service = rs.getString("service_name");

                LocalDate date = rs.getDate("date").toLocalDate();  // 👈 THIS LINE WAS MISSING

                totalCost += cost;
                byService.merge(service, cost, Double::sum);
                trend.add(Map.of("date", date.toString(), "cost", cost));
            }


            List<Map<String, Object>> serviceList = byService.entrySet().stream()
                    .map(e -> Map.of("service", (Object) e.getKey(), "costUSD", (Object) e.getValue()))
                    .toList();

            log.info("Fetched cost data for all accounts: totalCost={}, services={}",
                    totalCost, byService.size());

            return Map.of(
                    "accountId", "all",
                    "totals", Map.of("costUSD", totalCost, "lastMonth", totalCost * 0.85),
                    "byService", serviceList,
                    "trend", trend
            );

        } catch (SQLException e) {
            log.error("Failed to fetch cost data from Snowflake for all accounts: {}",
                    e.getMessage(), e);
            throw new SnowflakeException("Failed to fetch cost data for all accounts", e);
        }
    }

    // Fallback dummy data when Snowflake not configured
    private Map<String, Object> getDummyCostData(String accountId) {
        log.debug("Generating dummy cost data for account: {}", accountId);
        return Map.of(
                "accountId", accountId == null ? "all" : accountId,
                "totals", Map.of("costUSD", 1234.56, "lastMonth", 980.12),
                "byService", List.of(
                        Map.of("service", "EC2", "costUSD", 400.1),
                        Map.of("service", "S3", "costUSD", 234.5),
                        Map.of("service", "RDS", "costUSD", 600.0)
                ),
                "trend", List.of(
                        Map.of("date", "2025-12-01", "cost", 30.5),
                        Map.of("date", "2025-12-02", "cost", 35.2),
                        Map.of("date", "2025-12-03", "cost", 28.9)
                )
        );
    }

    private Map<String, Object> getDummyCostDataAllAccounts() {
        log.debug("Generating dummy cost data for all accounts");
        return Map.of(
                "accountId", "all",
                "totals", Map.of("costUSD", 5678.90, "lastMonth", 4321.00),
                "byService", List.of(
                        Map.of("service", "S3", "costUSD", 1200.5),
                        Map.of("service", "Lambda", "costUSD", 800.3),
                        Map.of("service", "EC2", "costUSD", 2000.1)
                ),
                "trend", List.of(
                        Map.of("date", "2025-12-01", "cost", 150.0),
                        Map.of("date", "2025-12-02", "cost", 180.5),
                        Map.of("date", "2025-12-03", "cost", 165.2)
                )
        );
    }
}