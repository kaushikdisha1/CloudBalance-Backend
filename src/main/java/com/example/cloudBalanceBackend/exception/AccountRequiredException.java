package com.example.cloudBalanceBackend.exception;

public class AccountRequiredException extends RuntimeException {
    public AccountRequiredException(String message) {

        super(message);
    }
}
