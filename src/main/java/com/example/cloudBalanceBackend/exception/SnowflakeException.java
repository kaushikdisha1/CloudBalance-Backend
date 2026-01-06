package com.example.cloudBalanceBackend.exception;

public class SnowflakeException extends RuntimeException {
    public SnowflakeException(String message, Throwable cause) {

        super(message, cause);
    }
}
