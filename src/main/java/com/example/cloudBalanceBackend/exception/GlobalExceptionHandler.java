package com.example.cloudBalanceBackend.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> makeBody(String error, String message, String code) {
        return Map.of(
                "error", error,
                "message", message,
                "code", code,
                "timestamp", Instant.now().toString()
        );
    }

    /* ================= VALIDATION ================= */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.badRequest()
                .body(makeBody("ValidationError", msg, "INVALID_INPUT"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<?> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(makeBody("ValidationError", ex.getMessage(), "INVALID_INPUT"));
    }

    /* ================= BUSINESS ERRORS ================= */

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<?> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(makeBody("BadRequest", ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<?> handleDuplicate(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(makeBody("Conflict", "Resource already exists", "DUPLICATE_RESOURCE"));
    }

    /* ================= ACCOUNT ERRORS ================= */

    @ExceptionHandler(AccountRequiredException.class)
    protected ResponseEntity<?> handleAccountRequired(AccountRequiredException ex) {
        return ResponseEntity.badRequest()
                .body(makeBody("AccountError", ex.getMessage(), "ACCOUNT_REQUIRED"));
    }

    @ExceptionHandler(AccountNotAssignedException.class)
    protected ResponseEntity<?> handleAccountNotAssigned(AccountNotAssignedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(makeBody("AccountError", ex.getMessage(), "ACCOUNT_NOT_ASSIGNED"));
    }

    /* ================= EXTERNAL SERVICE ERRORS ================= */

    @ExceptionHandler(AwsServiceException.class)
    protected ResponseEntity<?> handleAwsError(AwsServiceException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(makeBody("AwsError", "Data temporarily unavailable", "AWS_UNAVAILABLE"));
    }

    @ExceptionHandler(SnowflakeException.class)
    protected ResponseEntity<?> handleSnowflakeError(SnowflakeException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(makeBody("SnowflakeError", "Data temporarily unavailable", "SNOWFLAKE_UNAVAILABLE"));
    }

    /* ================= SECURITY ================= */

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<?> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(makeBody("Forbidden", "You are not allowed to perform this action", "ACCESS_DENIED"));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    protected ResponseEntity<?> handleAuthMissing() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(makeBody("Unauthorized", "Authentication required", "AUTH_REQUIRED"));
    }

    /* ================= FALLBACK ================= */

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<?> handleAll(Exception ex, WebRequest req) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(makeBody("ServerError", "Something went wrong", "INTERNAL_ERROR"));
    }
}
