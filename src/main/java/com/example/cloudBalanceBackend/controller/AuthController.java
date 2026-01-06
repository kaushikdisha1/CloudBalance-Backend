package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.*;
import com.example.cloudBalanceBackend.model.RevokedToken;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.repository.RevokedTokenRepository;
import com.example.cloudBalanceBackend.repository.UserRepository;
import com.example.cloudBalanceBackend.security.CustomUserDetailsService;
import com.example.cloudBalanceBackend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced Authentication Controller with UserDetailsService
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final RevokedTokenRepository revokedRepo;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Login endpoint - Option 1: Using AuthenticationManager (Recommended)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        log.info("Login attempt for email: {}", req.getEmail());

        if (req.getEmail() == null || req.getPassword() == null) {
            log.warn("Login failed: missing credentials");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing credentials"));
        }

        try {
            // Authenticate using Spring Security's AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
            );

            // Get authenticated user details
            CustomUserDetailsService.CustomUserDetails userDetails =
                    (CustomUserDetailsService.CustomUserDetails) authentication.getPrincipal();

            User user = userDetails.getUser();

            // Generate JWT token
            var claims = Map.<String, Object>of("role", user.getRole().name());
            String token = jwtUtil.generateToken(user.getId(), claims);

            long expirationMinutes = Long.parseLong(
                    System.getProperty("app.jwt.expiration-minutes", "15")
            );

            log.info("Login successful for user: {} with role: {}", user.getEmail(), user.getRole());

            return ResponseEntity.ok(new AuthResponse(
                    token,
                    expirationMinutes,
                    user.getRole().name()
            ));

        } catch (BadCredentialsException e) {
            log.warn("Login failed for email {}: invalid credentials", req.getEmail());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } catch (Exception e) {
            log.error("Login error for email {}: {}", req.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Authentication failed"));
        }
    }

    /**
     * Login endpoint - Option 2: Manual validation (Your current approach)
     * Keep this if you prefer manual control
     */
    @PostMapping("/login-manual")
    public ResponseEntity<?> loginManual(@RequestBody AuthRequest req) {
        log.info("Manual login attempt for email: {}", req.getEmail());

        if (req.getEmail() == null || req.getPassword() == null) {
            log.warn("Login failed: missing credentials");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing credentials"));
        }

        Optional<User> uOpt = userRepository.findByEmail(req.getEmail());
        if (uOpt.isEmpty()) {
            log.warn("Login failed: user not found - {}", req.getEmail());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        User user = uOpt.get();
        if (!encoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: incorrect password for user - {}", req.getEmail());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        var claims = Map.<String, Object>of("role", user.getRole().name());
        String token = jwtUtil.generateToken(user.getId(), claims);

        long expirationMinutes = Long.parseLong(
                System.getProperty("app.jwt.expiration-minutes", "15")
        );

        log.info("Manual login successful for user: {} with role: {}", user.getEmail(), user.getRole());

        return ResponseEntity.ok(new AuthResponse(
                token,
                expirationMinutes,
                user.getRole().name()
        ));
    }

    /**
     * Logout endpoint - revoke JWT token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("Logout request received");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Logout failed: no token provided");
            return ResponseEntity.badRequest().body(Map.of("error", "No token"));
        }

        String token = authHeader.substring(7);

        try {
            Instant exp = jwtUtil.getExpiration(token);

            RevokedToken r = RevokedToken.builder()
                    .id(UUID.randomUUID().toString())
                    .token(token)
                    .createdAt(Instant.now())
                    .expiresAt(exp)
                    .build();

            revokedRepo.save(r);

            log.info("Token revoked successfully");
            return ResponseEntity.ok(Map.of("ok", true));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid token"));
        }
    }
}