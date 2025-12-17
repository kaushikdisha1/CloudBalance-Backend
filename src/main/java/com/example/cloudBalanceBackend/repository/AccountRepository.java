package com.example.cloudBalanceBackend.repository;

import com.example.cloudBalanceBackend.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}