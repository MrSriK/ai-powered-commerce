package com.mrsrik.ai_powered_commerce.cart.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.mrsrik.ai_powered_commerce.cart.dto.CartItemDto;

/**
 * Pure calculation functions for shopping cart totals (Requirement 3.2, D-33).
 * Always computes with exact BigDecimal arithmetic scaled to 2 decimal places with HALF_UP rounding.
 */
public record CartTotals(int totalItems, BigDecimal totalAmount) {

    public CartTotals {
        if (totalAmount != null) {
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static CartTotals of(List<CartItemDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return new CartTotals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        int totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CartItemDto line : lines) {
            totalItems += line.quantity();
            if (line.subtotal() != null) {
                totalAmount = totalAmount.add(line.subtotal());
            }
        }
        return new CartTotals(totalItems, totalAmount.setScale(2, RoundingMode.HALF_UP));
    }

    public static BigDecimal computeSubtotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}

