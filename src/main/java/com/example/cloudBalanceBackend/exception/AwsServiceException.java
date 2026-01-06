package com.example.cloudBalanceBackend.exception;

public class AwsServiceException extends RuntimeException {
    public AwsServiceException(String message, Throwable cause) {

        super(message, cause);
    }
}
