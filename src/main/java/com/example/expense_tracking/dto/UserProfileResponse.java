package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private Boolean isActive;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}
