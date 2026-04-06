package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryResponse {
    private String categoryName;
    private BigDecimal total;
    private TransactionType type;
}
