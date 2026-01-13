package com.example.cloudBalanceBackend.dto;

import com.example.cloudBalanceBackend.model.Account;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AccountDto {
    private String id;
    private String accountName;
    private String accountNumber;
    private String arnNumber;
    private String provider;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountDto(Account acc) {
        this.id = acc.getId();
        this.accountName = acc.getName();
        this.accountNumber = acc.getProviderAccountId();
        this.provider = acc.getProvider();
        this.status = acc.getStatus();
        this.createdAt = acc.getCreatedAt();
        this.updatedAt = acc.getUpdatedAt();

        // Extract ARN from meta JSON
        if (acc.getMeta() != null && !acc.getMeta().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode metaNode = mapper.readTree(acc.getMeta());
                if (metaNode.has("arnNumber")) {
                    this.arnNumber = metaNode.get("arnNumber").asText();
                }
            } catch (Exception e) {
                // Silently ignore parsing errors
            }
        }
    }
}