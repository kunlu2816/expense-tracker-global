package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankConfigRepository extends JpaRepository<BankConfig, Long> {
    // Find by GoCardless account ID
    Optional<BankConfig> findByGocardlessAccountId(String gocardlessAccountId);

    // Find all bank configs for a user with a specific status
    List<BankConfig> findByUserAndStatus(User user, String status);

    // Find all bank config with specific status (for sync scheduler)
    List<BankConfig> findByStatus(String status);

    // Find all bank configs for a user
    List<BankConfig> findByUser(User user);

    // Find specific bank config owned by user (for ownership verification)
    Optional<BankConfig> findByIdAndUser(Long id, User user);

    // Find by requisition ID (for callback processing)
    Optional<BankConfig> findByRequisitionId(String requisitionId);

    // Find by user and requisition ID
    Optional<BankConfig> findByUserAndRequisitionId(User user, String requisitionId);
}
