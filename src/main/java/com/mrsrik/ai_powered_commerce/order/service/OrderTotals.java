package com.mrsrik.ai_powered_commerce.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.mrsrik.ai_powered_commerce.common.error.OrderTotalTooLargeException;

/**
 * Pure calculation functions for order totals and maximum total validation (Requirements 4.4, OQ-13, D-33).
 */
public record OrderTotals(int totalItems, BigDecimal totalAmount) {

    public static final BigDecimal MAX_ORDER_TOTAL = new BigDecimal("9999999999.99");

    public record Line(BigDecimal unitPrice, int quantity) {
    }

    public OrderTotals {
        if (totalAmount != null) {
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
            if (totalAmount.compareTo(MAX_ORDER_TOTAL) > 0) {
                throw new OrderTotalTooLargeException();
            }
        }
    }

    public static OrderTotals of(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return new OrderTotals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        int totalItems = 0;
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Line line : lines) {
            totalItems += line.quantity();
            if (line.unitPrice() != null) {
                BigDecimal subtotal = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity()))
                        .setScale(2, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(subtotal);
            }
        }
        return new OrderTotals(totalItems, totalAmount);
    }

    public static BigDecimal computeSubtotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}

