package com.mrsrik.ai_powered_commerce.cart.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record CartItemDto(
        UUID itemId,
        UUID productId,
        String productName,
        String brand,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public CartItemDto {
        if (unitPrice != null) {
            unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (subtotal != null) {
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

