package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.AccountDto;
import com.example.cloudBalanceBackend.dto.CreateAccountRequest;
import com.example.cloudBalanceBackend.model.Account;
import com.example.cloudBalanceBackend.model.Role;
import com.example.cloudBalanceBackend.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;


    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody CreateAccountRequest req, Authentication auth) {
        if (!accountService.hasRole(auth, Role.ADMIN)) return ResponseEntity.status(403).body(Map.of("error","Forbidden"));
        Account a = accountService.createAccount(req);
        return ResponseEntity.status(201).body(a);
    }

    @GetMapping
    public ResponseEntity<?> listAccounts(Authentication auth) {
        List<AccountDto> accounts = accountService.listAccounts(auth);
        return ResponseEntity.ok(accounts);
    }
}