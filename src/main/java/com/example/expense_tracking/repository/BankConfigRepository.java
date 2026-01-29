package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankConfigRepository extends JpaRepository<BankConfig, Long> {
    Optional<BankConfig> findByGocardlessAccountId(String gocardlessAccountId);

    List<BankConfig> findByUserAndStatus(User user, String status);

    List<BankConfig> findByStatus(String status);
}
