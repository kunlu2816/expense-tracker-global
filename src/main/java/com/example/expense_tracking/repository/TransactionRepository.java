package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.dto.CategorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction,Long> {
    Page<Transaction> findAllByUser(User user, Pageable pageable);

    @Query(value = "SELECT t FROM Transaction t LEFT JOIN FETCH t.category WHERE t.user = :user " +
            "AND (:category IS NULL OR t.category.name = :category) " +
            "AND (CAST(:startDate AS timestamp) IS NULL OR t.transactionDate >= :startDate) " +
            "AND (CAST(:endDate AS timestamp) IS NULL OR t.transactionDate <= :endDate)",
           countQuery = "SELECT COUNT(t) FROM Transaction t LEFT JOIN t.category c WHERE t.user = :user " +
            "AND (:category IS NULL OR c.name = :category) " +
            "AND (CAST(:startDate AS timestamp) IS NULL OR t.transactionDate >= :startDate) " +
            "AND (CAST(:endDate AS timestamp) IS NULL OR t.transactionDate <= :endDate)")
    Page<Transaction> findFilteredTransactions(
            @Param("user") User user,
            @Param("category") String category,
            @Param("startDate")LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t WHERE t.user = :user AND t.type = :type")
    BigDecimal calculateTotal(@Param("user") User user, @Param("type") TransactionType type);

    boolean existsByBankTransactionIdAndBankConfig(String bankTransactionId, BankConfig bankConfig);

    // Unlink all transactions from a bank config (set bankConfig to null)
    // Used when user unlinks a bank account but wants to keep transaction history
    @Modifying
    @Query("UPDATE Transaction t SET t.bankConfig = null WHERE t.bankConfig = :bankConfig")
    void unlinkTransactionsFromBankConfig(@Param("bankConfig") BankConfig bankConfig);

    // Nullify category on all transactions before deleting a category
    @Modifying
    @Query("UPDATE Transaction t SET t.category = null WHERE t.category = :category")
    void nullifyCategoryOnTransactions(@Param("category") Category category);

    @Query("SELECT new com.example.expense_tracking.dto.CategorySummaryResponse(c.name, SUM(t.amount), t.type) " +
            "FROM Transaction t JOIN t.category c WHERE t.user = :user GROUP BY c.name, t.type")
    List<CategorySummaryResponse> getCategorySummary(@Param("user") User user);
}
