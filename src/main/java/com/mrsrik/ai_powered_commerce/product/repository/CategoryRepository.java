package com.mrsrik.ai_powered_commerce.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.product.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByDeletedAtIsNullOrderByNameAsc();

    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Deliberately includes soft-deleted categories: name uniqueness ignores case and deleted_at (Requirement 2.12).
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Deliberately includes soft-deleted categories: name uniqueness ignores case and deleted_at (Requirement 2.12).
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}

