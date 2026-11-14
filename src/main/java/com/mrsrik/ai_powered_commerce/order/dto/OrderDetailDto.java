package com.mrsrik.ai_powered_commerce.order.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

/**
 * Detailed view of an order with shipping address and item snapshots (Requirement 4.12).
 */
public record OrderDetailDto(
        UUID id,
        OrderStatus status,
        int totalItems,
        BigDecimal totalAmount,
        String transactionId,
        Instant createdAt,
        Instant shippedAt,
        ShippingAddressDto shippingAddress,
        List<OrderItemDto> items) {

    public OrderDetailDto {
        if (totalAmount != null) {
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
        items = items != null ? List.copyOf(items) : List.of();
    }
}

