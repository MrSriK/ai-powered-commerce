package com.mrsrik.ai_powered_commerce.product.service;

import java.util.List;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.product.dto.CategoryDto;
import com.mrsrik.ai_powered_commerce.product.dto.CategoryRequest;

public interface CategoryService {

    /**
     * Lists all categories whose deletedAt is null, ordered by name ASC.
     */
    List<CategoryDto> list();

    /**
     * Creates a new category. Throws DuplicateCategoryNameException (409) if the name already exists
     * (case-insensitive, including soft-deleted categories).
     */
    CategoryDto create(CategoryRequest request);

    /**
     * Updates an existing non-deleted category. Throws CategoryNotFoundException (404) if missing or soft-deleted,
     * or DuplicateCategoryNameException (409) if another category has the same name (case-insensitive, including soft-deleted).
     */
    CategoryDto update(UUID id, CategoryRequest request);

    /**
     * Soft-deletes a category. Throws CategoryNotFoundException (404) if missing or soft-deleted,
     * or CategoryInUseException (409) if at least one active, non-deleted product references it.
     */
    void delete(UUID id);
}
