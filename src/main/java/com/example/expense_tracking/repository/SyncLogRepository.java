package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
    Page<SyncLog> findByBankConfigOrderBySyncedAtDesc(BankConfig bankConfig, Pageable pageable);

    Optional<SyncLog> findTopByBankConfigOrderBySyncedAtDesc(BankConfig bankConfig);
}
