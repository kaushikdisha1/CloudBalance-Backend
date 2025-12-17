package com.example.cloudBalanceBackend.exception;

public class AccountNotAssignedException extends RuntimeException {
    public AccountNotAssignedException(String message) {
        super(message);
    }
}
