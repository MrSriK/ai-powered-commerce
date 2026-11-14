package com.mrsrik.ai_powered_commerce.order.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Representation of an individual order item snapshot (Requirement 4.3, 4.12).
 */
public record OrderItemDto(
        UUID productId,
        String productName,
        String brand,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal) {

    public OrderItemDto {
        if (unitPrice != null) {
            unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (subtotal != null) {
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

