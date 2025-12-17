package com.example.cloudBalanceBackend.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;
}