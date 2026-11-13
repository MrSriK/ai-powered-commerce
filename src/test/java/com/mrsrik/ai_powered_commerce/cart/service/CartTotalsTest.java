package com.mrsrik.ai_powered_commerce.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrsrik.ai_powered_commerce.cart.dto.CartItemDto;

/**
 * Unit tests for CartTotals pure calculation functions (Requirement 3.2, Property 4).
 */
class CartTotalsTest {

    @Test
    void emptyOrNullListYieldsZeroTotals() {
        CartTotals empty = CartTotals.of(List.of());
        assertThat(empty.totalItems()).isZero();
        assertThat(empty.totalAmount()).isEqualByComparingTo("0.00");

        CartTotals fromNull = CartTotals.of(null);
        assertThat(fromNull.totalItems()).isZero();
        assertThat(fromNull.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void singleLineCalculatesSubtotalAndTotals() {
        BigDecimal unitPrice = new BigDecimal("19.99");
        int quantity = 3;
        BigDecimal expectedSubtotal = new BigDecimal("59.97");

        BigDecimal subtotal = CartTotals.computeSubtotal(unitPrice, quantity);
        assertThat(subtotal).isEqualByComparingTo(expectedSubtotal);

        CartItemDto line = new CartItemDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Product",
                "Brand",
                quantity,
                unitPrice,
                subtotal);

        CartTotals totals = CartTotals.of(List.of(line));
        assertThat(totals.totalItems()).isEqualTo(3);
        assertThat(totals.totalAmount()).isEqualByComparingTo(expectedSubtotal);
    }

    @Test
    void multipleLinesSumQuantitiesAndSubtotals() {
        CartItemDto line1 = new CartItemDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Product 1",
                "Brand",
                2,
                new BigDecimal("10.50"),
                new BigDecimal("21.00"));

        CartItemDto line2 = new CartItemDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Product 2",
                "Brand",
                5,
                new BigDecimal("5.25"),
                new BigDecimal("26.25"));

        CartTotals totals = CartTotals.of(List.of(line1, line2));
        assertThat(totals.totalItems()).isEqualTo(7);
        assertThat(totals.totalAmount()).isEqualByComparingTo("47.25");
    }

    @Test
    void computeSubtotalScalesHalfUp() {
        BigDecimal unitPrice = new BigDecimal("10.555");
        BigDecimal subtotal = CartTotals.computeSubtotal(unitPrice, 1);
        assertThat(subtotal).isEqualByComparingTo("10.56");
        assertThat(subtotal.scale()).isEqualTo(2);
    }
}

