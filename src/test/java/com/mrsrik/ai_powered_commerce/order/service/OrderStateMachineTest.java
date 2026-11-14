package com.mrsrik.ai_powered_commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.mrsrik.ai_powered_commerce.common.error.InvalidStatusTransitionException;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @Test
    @DisplayName("PENDING can transition to CONFIRMED and CANCELLED")
    void pendingAllowedTransitions() {
        assertThat(stateMachine.allowedFrom(OrderStatus.PENDING))
                .containsExactlyInAnyOrder(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        assertThat(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
        assertThat(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.requireTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED))
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(stateMachine.requireTransition(OrderStatus.PENDING, OrderStatus.CANCELLED))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("CONFIRMED can transition to SHIPPED and CANCELLED")
    void confirmedAllowedTransitions() {
        assertThat(stateMachine.allowedFrom(OrderStatus.CONFIRMED))
                .containsExactlyInAnyOrder(OrderStatus.SHIPPED, OrderStatus.CANCELLED);

        assertThat(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.SHIPPED)).isTrue();
        assertThat(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.requireTransition(OrderStatus.CONFIRMED, OrderStatus.SHIPPED))
                .isEqualTo(OrderStatus.SHIPPED);
        assertThat(stateMachine.requireTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("SHIPPED can transition to DELIVERED and CANCELLED")
    void shippedAllowedTransitions() {
        assertThat(stateMachine.allowedFrom(OrderStatus.SHIPPED))
                .containsExactlyInAnyOrder(OrderStatus.DELIVERED, OrderStatus.CANCELLED);

        assertThat(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED)).isTrue();
        assertThat(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isTrue();
        assertThat(stateMachine.requireTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED))
                .isEqualTo(OrderStatus.DELIVERED);
        assertThat(stateMachine.requireTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("DELIVERED is terminal: allowedFrom is empty and no transitions are permitted")
    void deliveredIsTerminal() {
        assertThat(stateMachine.allowedFrom(OrderStatus.DELIVERED)).isEmpty();
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(stateMachine.canTransition(OrderStatus.DELIVERED, target)).isFalse();
            assertThatThrownBy(() -> stateMachine.requireTransition(OrderStatus.DELIVERED, target))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Test
    @DisplayName("CANCELLED is terminal: allowedFrom is empty and no transitions are permitted")
    void cancelledIsTerminal() {
        assertThat(stateMachine.allowedFrom(OrderStatus.CANCELLED)).isEmpty();
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(stateMachine.canTransition(OrderStatus.CANCELLED, target)).isFalse();
            assertThatThrownBy(() -> stateMachine.requireTransition(OrderStatus.CANCELLED, target))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("Same-state transitions are strictly illegal for all statuses")
    void sameStateTransitionsAreIllegal(OrderStatus status) {
        assertThat(stateMachine.canTransition(status, status)).isFalse();
        assertThatThrownBy(() -> stateMachine.requireTransition(status, status))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("Disallowed skips and backwards moves are rejected")
    void disallowedSkipsAndBackwardsMoves() {
        // PENDING skips
        assertThat(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.SHIPPED)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED)).isFalse();

        // CONFIRMED backward or skip
        assertThat(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PENDING)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.DELIVERED)).isFalse();

        // SHIPPED backward
        assertThat(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.PENDING)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("Null arguments return false or empty set gracefully")
    void nullArgumentsHandledGracefully() {
        assertThat(stateMachine.canTransition(null, OrderStatus.PENDING)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.PENDING, null)).isFalse();
        assertThat(stateMachine.canTransition(null, null)).isFalse();
        assertThat(stateMachine.allowedFrom(null)).isEmpty();

        assertThatThrownBy(() -> stateMachine.requireTransition(null, OrderStatus.PENDING))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> stateMachine.requireTransition(OrderStatus.PENDING, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}

