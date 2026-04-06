package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.CategoryDTO;
import com.example.expense_tracking.dto.CategoryRequest;
import com.example.expense_tracking.entity.Category;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ConflictException;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.repository.CategoryRepository;
import com.example.expense_tracking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    // Get all categories for a user
    public List<CategoryDTO> getUserCategories(User user) {
        return categoryRepository.findByUser(user).stream()
                .map(this::mapToCategoryDTO)
                .toList();
    }

    // Create a new category
    @Transactional
    public CategoryDTO createCategory(User user, CategoryRequest request) {
        // Check if category with same name already exists for this user
        categoryRepository.findByNameAndUser(request.getName(), user)
                .ifPresent(existing -> {
                    throw new ConflictException("Category '" + request.getName() + "' already exists");
                });

        Category category = Category.builder()
                .user(user)
                .name(request.getName())
                .type(request.getType())
                .icon(request.getIcon())
                .build();

        Category saved = categoryRepository.save(category);
        return mapToCategoryDTO(saved);
    }

    // Update a category
    @Transactional
    public CategoryDTO updateCategory(User user, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        category.setName(request.getName());
        category.setType(request.getType());
        category.setIcon(request.getIcon());

        Category saved = categoryRepository.save(category);
        return mapToCategoryDTO(saved);
    }

    // Delete a category
    @Transactional
    public void deleteCategory(User user, Long categoryId) {
        Category category = categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Nullify category on all transactions before deleting
        transactionRepository.nullifyCategoryOnTransactions(category);
        categoryRepository.delete(category);
    }

    private CategoryDTO mapToCategoryDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .build();
    }
}
