package com.mrsrik.ai_powered_commerce.cart.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;

import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId and c.deletedAt is null")
    Optional<Cart> findForUpdateByUserId(@Param("userId") UUID userId);
}

