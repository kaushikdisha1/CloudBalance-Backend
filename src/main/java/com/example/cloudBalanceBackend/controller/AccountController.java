package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.dto.CreateAccountRequest;
import com.example.cloudBalanceBackend.model.Account;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.security.CustomUserDetailsService;
import com.example.cloudBalanceBackend.service.AccountService;
import com.example.cloudBalanceBackend.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Account> createAccount(
            @RequestBody CreateAccountRequest req,
            Authentication auth
    ) {
        // Use SecurityUtils to get current user ID
        String actorId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(201)
                .body(accountService.createAccount(req, actorId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','READ_ONLY','CUSTOMER')")
    public ResponseEntity<List<AccountDto>> listAccounts(Authentication auth) {

        // Use SecurityUtils to get current user
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(accountService.listVisibleAccounts(user));
    }
}