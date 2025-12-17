package com.example.cloudBalanceBackend.controller;

import com.example.cloudBalanceBackend.dto.*;
import com.example.cloudBalanceBackend.model.RevokedToken;
import com.example.cloudBalanceBackend.model.User;
import com.example.cloudBalanceBackend.repository.RevokedTokenRepository;
import com.example.cloudBalanceBackend.repository.UserRepository;
import com.example.cloudBalanceBackend.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final RevokedTokenRepository revokedRepo;

    public AuthController(UserRepository users, BCryptPasswordEncoder encoder, JwtUtil jwtUtil, RevokedTokenRepository revokedRepo) {
        this.users = users;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.revokedRepo = revokedRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        if (req.getEmail() == null || req.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing credentials"));
        }
        Optional<User> uOpt = users.findByEmail(req.getEmail());
        if (uOpt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        User user = uOpt.get();
        if (!encoder.matches(req.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        var claims = Map.<String,Object>of("role", user.getRole().name());
        String token = jwtUtil.generateToken(user.getId(), claims);
        return ResponseEntity.ok(new AuthResponse(token, jwtUtil == null ? 15L : Long.parseLong(System.getProperty("app.jwt.expiration-minutes", "15")), user.getRole().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return ResponseEntity.badRequest().body(Map.of("error","No token"));
        String token = authHeader.substring(7);
        Instant exp;
        try {
            exp = jwtUtil.getExpiration(token);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error","Invalid token"));
        }
        RevokedToken r = RevokedToken.builder().id(UUID.randomUUID().toString()).token(token).createdAt(Instant.now()).expiresAt(exp).build();
        revokedRepo.save(r);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}