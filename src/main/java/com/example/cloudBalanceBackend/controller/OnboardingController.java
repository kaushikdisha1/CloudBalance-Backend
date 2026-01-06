package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountDto>> onboardAccounts(@RequestBody List<String> accountIds,
                                                            Authentication auth) {
        List<AccountDto> created = onboardingService.onboardAccounts(accountIds, auth.getName());
        return ResponseEntity.ok(created); // Explicitly checking status
    }

    @GetMapping("/orphan-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY')")
    public ResponseEntity<List<AccountDto>> getOrphanAccounts() {
        return ResponseEntity.ok(onboardingService.getOrphanAccounts());
    }
}
