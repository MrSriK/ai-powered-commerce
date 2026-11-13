package com.mrsrik.ai_powered_commerce.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mrsrik.ai_powered_commerce.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /**
     * Active, non-deleted product lookup used for public reads, cart and checkout.
     */
    Optional<Product> findByIdAndActiveTrueAndDeletedAtIsNull(UUID id);

    /**
     * Non-deleted product lookup used for admin update and delete (inactive allowed).
     */
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Deliberately includes soft-deleted products: SKUs are never reused (Requirement 2.9).
     */
    boolean existsBySku(String sku);

    /**
     * Deliberately includes soft-deleted products: SKUs are never reused (Requirement 2.9).
     */
    boolean existsBySkuAndIdNot(String sku, UUID id);

    /**
     * Category-in-use check: counts ACTIVE, non-deleted products only (Requirement 2.13).
     */
    boolean existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(UUID categoryId);
}

