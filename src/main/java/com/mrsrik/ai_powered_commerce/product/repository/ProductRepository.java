package com.mrsrik.ai_powered_commerce.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Atomic restocking query used on order cancellation (Requirement 4.15).
     * Deliberately contains no active or deleted filter: restocking restores goods to existing products
     * even if deactivated or soft-deleted (Requirement 4.18, D-67).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int incrementStock(@Param("id") UUID id, @Param("qty") int qty);

    /**
     * Atomic stock decrement for checkout (Requirements 4.6, 4.9).
     * Conditionally updates only if product is active, not deleted, and current stock >= qty.
     * Returns 1 if stock was decremented and the row lock was acquired; 0 if stock was insufficient
     * or product unavailable.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty and p.active = true and p.deletedAt is null")
    int decrementStockIfAvailable(@Param("id") UUID id, @Param("qty") int qty);
}

