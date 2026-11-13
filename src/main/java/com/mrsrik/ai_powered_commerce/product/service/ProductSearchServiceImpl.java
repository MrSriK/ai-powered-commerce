package com.mrsrik.ai_powered_commerce.product.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.CategoryRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductSpecs;

/**
 * Read-only implementation of {@link ProductSearchService} for AI module consumption (Requirements 7.3, 7.10).
 * Queries active, non-deleted products sorted deterministically by name ASC, id ASC, resolving category
 * names without N+1 queries.
 */
@Service
@Transactional(readOnly = true)
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductSearchServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<ProductSearchResult> findActiveProducts(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "name", "id"));
        List<Product> products = productRepository.findAll(ProductSpecs.activeAndNotDeleted(), pageable).getContent();
        if (products.isEmpty()) {
            return List.of();
        }

        Set<UUID> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());

        Map<UUID, String> categoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return products.stream()
                .map(p -> new ProductSearchResult(
                        p.getId(),
                        p.getName(),
                        p.getBrand(),
                        categoryNames.getOrDefault(p.getCategoryId(), ""),
                        p.getSellingPrice(),
                        p.getDescription()))
                .toList();
    }
}

