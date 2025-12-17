package com.example.cloudBalanceBackend.dto;

import lombok.Data;

@Data
public class CreateAccountRequest {
    private String name;
    private String provider;
    private String providerAccountId;
    private String meta;
}