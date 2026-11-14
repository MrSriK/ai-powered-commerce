package com.mrsrik.ai_powered_commerce.order.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Optional<Order> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.deletedAt is null")
    Optional<Order> findForUpdateById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.userId = :userId and o.deletedAt is null")
    Optional<Order> findForUpdateByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select o.id from Order o where o.status = :status and o.deletedAt is null order by o.createdAt, o.id")
    List<UUID> findIdsByStatus(@Param("status") OrderStatus status, Pageable batch);

    @Query("select o.id from Order o where o.status = com.mrsrik.ai_powered_commerce.order.enums.OrderStatus.SHIPPED " +
           "and o.deletedAt is null and o.shippedAt <= :cutoff order by o.shippedAt, o.id")
    List<UUID> findDeliverableIds(@Param("cutoff") Instant cutoff, Pageable batch);
}

