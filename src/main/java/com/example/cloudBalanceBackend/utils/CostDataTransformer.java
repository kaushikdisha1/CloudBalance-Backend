package com.example.cloudBalanceBackend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class CostDataTransformer {

    /**
     * Transform Snowflake data to frontend format
     */
    public Map<String, Object> transform(List<Map<String, Object>> data, String groupBy) {

        log.info("Transforming {} rows for groupBy: {}", data.size(), groupBy);

        Map<String, Map<String, Double>> groupedData = new LinkedHashMap<>();
        Set<String> monthsSet = new TreeSet<>();

        for (Map<String, Object> row : data) {
            String group = (String) row.get("group");
            String month = (String) row.get("month");
            Object costObj = row.get("cost");

            if (group == null || month == null || costObj == null) {
                continue;
            }

            Double cost = convertToDouble(costObj);
            monthsSet.add(month);

            groupedData.putIfAbsent(group, new HashMap<>());
            groupedData.get(group).put(month, cost);
        }

        List<Map<String, Object>> formattedData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entry : groupedData.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", entry.getKey());

            for (String month : monthsSet) {
                row.put(month, entry.getValue().getOrDefault(month, 0.0));
            }

            formattedData.add(row);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", formattedData);
        response.put("months", new ArrayList<>(monthsSet));
        response.put("groupBy", groupBy);

        return response;
    }

    private Double convertToDouble(Object costObj) {
        if (costObj instanceof Double) {
            return (Double) costObj;
        } else if (costObj instanceof Integer) {
            return ((Integer) costObj).doubleValue();
        } else if (costObj instanceof Long) {
            return ((Long) costObj).doubleValue();
        } else if (costObj instanceof Float) {
            return ((Float) costObj).doubleValue();
        }
        return 0.0;
    }
}