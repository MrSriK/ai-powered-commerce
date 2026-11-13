package com.mrsrik.ai_powered_commerce.product.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.common.error.CategoryInUseException;
import com.mrsrik.ai_powered_commerce.common.error.CategoryNotFoundException;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintNames;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintViolations;
import com.mrsrik.ai_powered_commerce.common.error.DuplicateCategoryNameException;
import com.mrsrik.ai_powered_commerce.product.dto.CategoryDto;
import com.mrsrik.ai_powered_commerce.product.dto.CategoryRequest;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.repository.CategoryRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            EntityManager entityManager,
            Clock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public List<CategoryDto> list() {
        List<Category> categories = categoryRepository.findByDeletedAtIsNullOrderByNameAsc();
        return categories.stream()
                .map(c -> new CategoryDto(c.getId(), c.getName()))
                .toList();
    }

    @Override
    @Transactional
    public CategoryDto create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateCategoryNameException();
        }

        Category category = Category.builder()
                .name(request.name())
                .build();

        try {
            Category saved = categoryRepository.saveAndFlush(category);
            entityManager.refresh(saved);
            return new CategoryDto(saved.getId(), saved.getName());
        } catch (DataIntegrityViolationException e) {
            if (isCategoryNameConstraintViolation(e)) {
                throw new DuplicateCategoryNameException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public CategoryDto update(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(CategoryNotFoundException::new);

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new DuplicateCategoryNameException();
        }

        category.setName(request.name());

        try {
            Category saved = categoryRepository.saveAndFlush(category);
            entityManager.refresh(saved);
            return new CategoryDto(saved.getId(), saved.getName());
        } catch (DataIntegrityViolationException e) {
            if (isCategoryNameConstraintViolation(e)) {
                throw new DuplicateCategoryNameException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(CategoryNotFoundException::new);

        if (productRepository.existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(id)) {
            throw new CategoryInUseException();
        }

        category.setDeletedAt(Instant.now(clock));
        categoryRepository.save(category);
    }

    private boolean isCategoryNameConstraintViolation(DataIntegrityViolationException e) {
        return ConstraintViolations.constraintName(e)
                .filter(ConstraintNames.CATEGORIES_NAME::equals)
                .isPresent();
    }
}
