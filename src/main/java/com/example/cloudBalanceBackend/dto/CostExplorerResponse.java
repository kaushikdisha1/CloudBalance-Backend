package com.example.cloudBalanceBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CostExplorerResponse {
    private String group;
    private String month;
    private Double cost;
}
