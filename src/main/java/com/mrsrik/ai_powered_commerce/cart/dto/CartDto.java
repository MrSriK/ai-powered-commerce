package com.mrsrik.ai_powered_commerce.cart.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record CartDto(
        UUID cartId,
        List<CartItemDto> items,
        int totalItems,
        BigDecimal totalAmount) {

    public CartDto {
        if (totalAmount != null) {
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
        items = items != null ? List.copyOf(items) : List.of();
    }
}

