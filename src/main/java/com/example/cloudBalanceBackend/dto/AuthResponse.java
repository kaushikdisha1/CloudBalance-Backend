package com.example.cloudBalanceBackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private long expiresInMinutes;
    private String role;
}