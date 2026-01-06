package com.example.cloudBalanceBackend.dto;

import com.example.cloudBalanceBackend.model.Account;
import java.time.Instant;

public class AccountDto {
    private String id;
    private String status;
    private Instant createdAt;

    public AccountDto(Account acc) {
        this.id = acc.getId();
        this.status = acc.getStatus();
        this.createdAt = acc.getCreatedAt();
    }

    // getters
    public String getId() {

        return id;
    }
    public String getStatus() {

        return status;
    }
    public Instant getCreatedAt() {

        return createdAt;
    }
}
