package com.mrsrik.ai_powered_commerce.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID orderId);
}

