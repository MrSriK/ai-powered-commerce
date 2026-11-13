package com.mrsrik.ai_powered_commerce.cart.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);

    @Modifying
    @Query("delete from CartItem ci where ci.cartId = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);
}
