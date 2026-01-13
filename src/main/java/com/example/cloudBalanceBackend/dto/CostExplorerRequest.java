package com.example.cloudBalanceBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CostExplorerRequest {
    private String groupBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<String, List<String>> filters;
    private String accountId;

}
