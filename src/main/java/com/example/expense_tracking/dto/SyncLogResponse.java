package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncLogResponse {
    private Long id;
    private LocalDateTime syncedAt;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer transactionsFetched;
    private Integer transactionsNew;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
