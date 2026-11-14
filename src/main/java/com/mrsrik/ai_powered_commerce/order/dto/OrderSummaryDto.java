package com.mrsrik.ai_powered_commerce.order.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

/**
 * Summary view of an order used in paginated order listings (Requirement 4.10).
 */
public record OrderSummaryDto(
        UUID id,
        OrderStatus status,
        int totalItems,
        BigDecimal totalAmount,
        String transactionId,
        Instant createdAt,
        Instant shippedAt) {

    public OrderSummaryDto {
        if (totalAmount != null) {
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

