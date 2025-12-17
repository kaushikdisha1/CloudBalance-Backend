package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/cost-explorer")
    public ResponseEntity<Map<String, Object>> costExplorer(
            @RequestParam(required = false) String accountId,
            Authentication auth) {

        return ResponseEntity.ok(
                dashboardService.getCostExplorer(auth, accountId)
        );
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
