package com.example.cloudBalanceBackend.repository;

import com.example.cloudBalanceBackend.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    Optional<RevokedToken> findByToken(String token);
}