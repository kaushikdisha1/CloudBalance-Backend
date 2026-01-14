package com.example.cloudBalanceBackend.utils;

import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.security.CustomUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Utility class for accessing the currently authenticated user
@Slf4j
public class SecurityUtils {

    private SecurityUtils() {
        // Utility class - no instantiation
    }

    // Get the current authenticated user's ID
    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("No authenticated user found");
            return null;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserDetailsService.CustomUserDetails) {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) principal;
            return userDetails.getUserId();
        }

        // Fallback for cases where principal is a string (shouldn't happen with new setup)
        if (principal instanceof String) {
            return (String) principal;
        }

        log.warn("Unexpected principal type: {}", principal.getClass().getName());
        return null;
    }

    // Get the current authenticated User entity
    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("No authenticated user found");
            return null;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserDetailsService.CustomUserDetails) {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) principal;
            return userDetails.getUser();
        }

        log.warn("Unable to extract User from principal type: {}",
                principal != null ? principal.getClass().getName() : "null");
        return null;
    }

    // Get the current authenticated user's email
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            log.warn("No authenticated user found");
            return null;
        }

        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserDetailsService.CustomUserDetails) {
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) principal;
            return userDetails.getUsername(); // username is email
        }

        return null;
    }

    // Check if current user is authenticated
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String);
    }
}