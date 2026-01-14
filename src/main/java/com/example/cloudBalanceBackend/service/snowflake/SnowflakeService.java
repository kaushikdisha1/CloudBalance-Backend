package com.example.cloudBalanceBackend.service.snowflake;

import com.example.cloudBalanceBackend.exception.AnalyticsQueryException;
import com.snowflake.snowpark.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnowflakeService {

    private final Session session;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Map frontend groupBy to Snowflake columns
    private static final Map<String, String> GROUP_BY_MAPPING = Map.of(
            "service", "SERVICE",
            "instanceType", "INSTANCE_TYPE",
            "accountId", "ACCOUNT_ID",
            "usageType", "USAGE_TYPE",
            "platform", "PLATFORM",
            "region", "REGION",
            "usageTypeGroup", "USAGE_TYPE_GROUP",
            "purchaseOption", "PURCHASE_OPTION",
            "resource", "RESOURCE",
            "availabilityZone", "AVAILABILITY_ZONE"
    );

    // Fetch cost data grouped by the given column and date range with optional filters
    public List<Map<String, Object>> getCostData(
            String groupBy,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, List<String>> filters
    ) {
        String columnName = GROUP_BY_MAPPING.get(groupBy);
        if (columnName == null) throw new IllegalArgumentException("Invalid groupBy: " + groupBy);

        String sql = buildSQLQuery(columnName, startDate, endDate, filters);
        log.info("Executing SQL: {}", sql);

        List<Map<String, Object>> transformedData = new ArrayList<>();
        try {
            var df = session.sql(sql);
            var iter = df.toLocalIterator();
            while (iter.hasNext()) {
                var r = iter.next();
                Map<String, Object> row = new HashMap<>();
                row.put("group", r.get(0) != null ? r.get(0).toString() : "");
                row.put("month", r.getString(1));
                Object costObj = r.get(2);
                row.put("cost", costObj instanceof Number ? ((Number) costObj).doubleValue() : 0.0);
                transformedData.add(row);
            }
            log.info("Successfully fetched {} rows", transformedData.size());
            return transformedData;
        } catch (Exception e) {
            log.error("Snowflake query failed", e);
            throw new RuntimeException("Failed to fetch cost data: " + e.getMessage(), e);
        }
    }

    // Build SQL query dynamically based on groupBy, date range, and filters
    private String buildSQLQuery(
            String columnName,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, List<String>> filters
    ) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ")
                .append(columnName).append(" AS GROUP_NAME, ")
                .append("TO_VARCHAR(BILL_DATE, 'YYYY-MM') AS MONTH, ")
                .append("SUM(COST) AS COSTS ")
                .append("FROM SNOWFLAKE_LEARNING_DB.AWS_CUR.COSTREPORT ")
                .append("WHERE BILL_DATE BETWEEN '")
                .append(startDate.format(DATE_FORMATTER))
                .append("' AND '")
                .append(endDate.format(DATE_FORMATTER))
                .append("' ");

        // Add filters dynamically
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    String filterColumn = GROUP_BY_MAPPING.getOrDefault(
                            entry.getKey(),
                            entry.getKey().toUpperCase()
                    );

                    String values = entry.getValue().stream()
                            .map(v -> "'" + v.replace("'", "''") + "'")
                            .collect(Collectors.joining(", "));

                    sql.append("AND ")
                            .append(filterColumn)
                            .append(" IN (")
                            .append(values)
                            .append(") ");
                }
            }
        }

        sql.append("GROUP BY ")
                .append(columnName).append(", ")
                .append("TO_VARCHAR(BILL_DATE, 'YYYY-MM') ")
                .append("ORDER BY TO_VARCHAR(BILL_DATE, 'YYYY-MM')");

        return sql.toString();
    }

    // Test Snowflake connection and fetch sample data
    public List<Map<String, Object>> testConnection() {
        log.info("Testing Snowflake connection...");

        try {
            Row[] rows = session.sql(
                    "SELECT * FROM SNOWFLAKE_LEARNING_DB.AWS_CUR.COSTREPORT  LIMIT 5"
            ).collect();

            log.info("Connection test successful. Retrieved {} rows", rows.length);

            List<Map<String, Object>> transformed = Arrays.stream(rows)
                    .map(r -> {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 0; i < r.size(); i++) {
                            Object val = r.get(i);
                            row.put("COL_" + i, val != null ? val.toString() : null);
                        }
                        return row;
                    })
                    .collect(Collectors.toList());

            return transformed;

        } catch (Exception e) {
            log.error("Snowflake connection test failed: {}", e.getMessage(), e);
            throw new AnalyticsQueryException("Snowflake connection failed: " + e.getMessage(), e);
        }
    }
}
