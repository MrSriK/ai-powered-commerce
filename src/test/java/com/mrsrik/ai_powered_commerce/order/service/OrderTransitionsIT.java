package com.mrsrik.ai_powered_commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.mrsrik.ai_powered_commerce.common.error.InvalidStatusTransitionException;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;

class OrderTransitionsIT extends AbstractPostgresIT {

    @Autowired
    private OrderTransitions orderTransitions;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate tx;

    private static final Set<String> VALID_TRANSITIONS = Set.of(
            "PENDING->CONFIRMED",
            "PENDING->CANCELLED",
            "CONFIRMED->SHIPPED",
            "CONFIRMED->CANCELLED",
            "SHIPPED->DELIVERED",
            "SHIPPED->CANCELLED"
    );

    @Test
    @DisplayName("Property 11: Validates all 25 (from, to) order status transition pairs")
    void property11All25StatusPairs() {
        User user = testData.user();

        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                String pair = from + "->" + to;
                boolean isValid = VALID_TRANSITIONS.contains(pair);

                tx.executeWithoutResult(status -> {
                    Order order = testData.order(user.getId(), from);

                    if (isValid) {
                        orderTransitions.apply(order, to);
                        assertThat(order.getStatus())
                                .as("Transition %s should succeed", pair)
                                .isEqualTo(to);

                        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
                        assertThat(reloaded.getStatus()).isEqualTo(to);
                    } else {
                        assertThatThrownBy(() -> orderTransitions.apply(order, to))
                                .as("Transition %s should be rejected with 409", pair)
                                .isInstanceOf(InvalidStatusTransitionException.class);

                        assertThat(order.getStatus())
                                .as("Order status should remain unchanged on failure for %s", pair)
                                .isEqualTo(from);
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("Property 37: shipped_at is set exactly upon transitioning to SHIPPED and never overwritten")
    void property37ShippedAtLifecycle() {
        User user = testData.user();

        tx.executeWithoutResult(status -> {
            Order order = testData.order(user.getId(), OrderStatus.PENDING);
            assertThat(order.getShippedAt()).isNull();

            // 1. PENDING -> CONFIRMED (shippedAt remains null)
            orderTransitions.apply(order, OrderStatus.CONFIRMED);
            assertThat(order.getShippedAt()).isNull();

            // 2. CONFIRMED -> SHIPPED (shippedAt is set)
            orderTransitions.apply(order, OrderStatus.SHIPPED);
            Instant firstShippedAt = order.getShippedAt();
            assertThat(firstShippedAt).isNotNull();

            // 3. SHIPPED -> DELIVERED (shippedAt is preserved, never overwritten)
            orderTransitions.apply(order, OrderStatus.DELIVERED);
            assertThat(order.getShippedAt()).isEqualTo(firstShippedAt);
        });

        // Cancelled before shipping keeps null shippedAt
        tx.executeWithoutResult(status -> {
            Order order = testData.order(user.getId(), OrderStatus.PENDING);
            orderTransitions.apply(order, OrderStatus.CANCELLED);
            assertThat(order.getShippedAt()).isNull();
        });

        // Cancelled after shipping retains original shippedAt
        tx.executeWithoutResult(status -> {
            Order order = testData.order(user.getId(), OrderStatus.PENDING);
            orderTransitions.apply(order, OrderStatus.CONFIRMED);
            orderTransitions.apply(order, OrderStatus.SHIPPED);
            Instant shippedAt = order.getShippedAt();
            assertThat(shippedAt).isNotNull();

            orderTransitions.apply(order, OrderStatus.CANCELLED);
            assertThat(order.getShippedAt()).isEqualTo(shippedAt);
        });
    }

    @Test
    @DisplayName("Order cancellation restocks active, inactive, and soft-deleted products, skipping null or missing products")
    void cancellationRestockingBehavior() {
        User user = testData.user();
        Category category = testData.category();

        Product activeProduct = testData.product(category.getId(), b -> b.stock(10).active(true));
        Product inactiveProduct = testData.product(category.getId(), b -> b.stock(5).active(false));
        Product deletedProduct = testData.product(category.getId(), b -> b.stock(2).deletedAt(Instant.now()));

        tx.executeWithoutResult(status -> {
            Order order = testData.order(user.getId(), OrderStatus.PENDING);

            // Add order items
            testData.orderItem(order.getId(), activeProduct.getId(), 3);
            testData.orderItem(order.getId(), inactiveProduct.getId(), 2);
            testData.orderItem(order.getId(), deletedProduct.getId(), 1);
            testData.orderItem(order.getId(), null, 4); // null productId (skipped per R4.18)

            // Cancel order
            orderTransitions.apply(order, OrderStatus.CANCELLED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });

        // Verify stock after restock
        Product reloadedActive = productRepository.findById(activeProduct.getId()).orElseThrow();
        assertThat(reloadedActive.getStock()).isEqualTo(13); // 10 + 3

        Product reloadedInactive = productRepository.findById(inactiveProduct.getId()).orElseThrow();
        assertThat(reloadedInactive.getStock()).isEqualTo(7); // 5 + 2

        Product reloadedDeleted = productRepository.findById(deletedProduct.getId()).orElseThrow();
        assertThat(reloadedDeleted.getStock()).isEqualTo(3); // 2 + 1
    }

    @Test
    @DisplayName("Rejected cancellation on DELIVERED or CANCELLED order changes no stock")
    void rejectedCancellationLeavesStockUnchanged() {
        User user = testData.user();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(20));

        tx.executeWithoutResult(status -> {
            Order order = testData.order(user.getId(), OrderStatus.DELIVERED);
            testData.orderItem(order.getId(), product.getId(), 5);

            assertThatThrownBy(() -> orderTransitions.apply(order, OrderStatus.CANCELLED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        });

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(20); // untouched
    }
}

