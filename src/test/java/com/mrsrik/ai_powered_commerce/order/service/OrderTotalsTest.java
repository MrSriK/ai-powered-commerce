package com.mrsrik.ai_powered_commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mrsrik.ai_powered_commerce.common.error.OrderTotalTooLargeException;

class OrderTotalsTest {

    @Test
    @DisplayName("Empty or null lines produce zero totals with scale 2")
    void emptyOrNullLinesProduceZero() {
        OrderTotals nullTotals = OrderTotals.of(null);
        assertThat(nullTotals.totalItems()).isZero();
        assertThat(nullTotals.totalAmount()).isEqualByComparingTo("0.00");

        OrderTotals emptyTotals = OrderTotals.of(List.of());
        assertThat(emptyTotals.totalItems()).isZero();
        assertThat(emptyTotals.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Calculates total items and amounts accurately with HALF_UP rounding")
    void calculatesTotalsWithHalfUpRounding() {
        List<OrderTotals.Line> lines = List.of(
                new OrderTotals.Line(new BigDecimal("19.99"), 2),   // 39.98
                new OrderTotals.Line(new BigDecimal("10.005"), 1),  // 10.01 rounded HALF_UP
                new OrderTotals.Line(new BigDecimal("5.50"), 3)     // 16.50
        );

        OrderTotals totals = OrderTotals.of(lines);
        assertThat(totals.totalItems()).isEqualTo(6);
        assertThat(totals.totalAmount()).isEqualByComparingTo("66.49");
    }

    @Test
    @DisplayName("Maximum order total of 9999999999.99 is allowed")
    void maxOrderTotalAllowed() {
        OrderTotals totals = new OrderTotals(1, new BigDecimal("9999999999.99"));
        assertThat(totals.totalAmount()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    @DisplayName("Order total exceeding 9999999999.99 throws OrderTotalTooLargeException")
    void exceedingMaxOrderTotalThrowsException() {
        assertThatThrownBy(() -> new OrderTotals(1, new BigDecimal("10000000000.00")))
                .isInstanceOf(OrderTotalTooLargeException.class);

        List<OrderTotals.Line> lines = List.of(
                new OrderTotals.Line(new BigDecimal("5000000000.00"), 3) // 15,000,000,000.00
        );
        assertThatThrownBy(() -> OrderTotals.of(lines))
                .isInstanceOf(OrderTotalTooLargeException.class);
    }

    @Test
    @DisplayName("computeSubtotal correctly computes unitPrice * quantity with scale 2")
    void computeSubtotalHelper() {
        assertThat(OrderTotals.computeSubtotal(null, 5)).isEqualByComparingTo("0.00");
        assertThat(OrderTotals.computeSubtotal(new BigDecimal("12.345"), 2)).isEqualByComparingTo("24.69");
    }
}

