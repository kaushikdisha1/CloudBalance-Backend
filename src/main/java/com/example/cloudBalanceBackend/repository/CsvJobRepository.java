package com.example.cloudBalanceBackend.repository;

import com.example.cloudBalanceBackend.model.CsvJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvJobRepository extends JpaRepository<CsvJob, String> {
}
