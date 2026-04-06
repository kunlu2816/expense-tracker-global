package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.ChangePasswordRequest;
import com.example.expense_tracking.dto.UpdateProfileRequest;
import com.example.expense_tracking.dto.UserProfileResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.BadRequestException;
import com.example.expense_tracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getProfile(User user) {
        return mapToProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(User user, UpdateProfileRequest request) {
        user.setFullName(request.getFullName());
        User saved = userRepository.save(user);
        return mapToProfileResponse(saved);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        // Verify if the OLD password is correct
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Hash the new password
        String newHash = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHash(newHash);

        userRepository.save(user);
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .isActive(user.getIsActive())
                .lastActiveAt(user.getLastActiveAt())
                .createdAt(user.getCreated_at())
                .build();
    }
}
