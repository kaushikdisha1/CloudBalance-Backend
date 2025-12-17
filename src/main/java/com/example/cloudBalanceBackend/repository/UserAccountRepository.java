package com.example.cloudBalanceBackend.repository;

import com.example.cloudBalanceBackend.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    List<UserAccount> findByUserId(String userId);
    Optional<UserAccount> findByUserIdAndAccountId(String userId, String accountId);
    List<UserAccount> findByAccountId(String accountId);
}