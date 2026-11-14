package com.mrsrik.ai_powered_commerce.order.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mrsrik.ai_powered_commerce.common.error.InvalidStatusTransitionException;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

/**
 * Pure state machine enforcing permitted order lifecycle status transitions (Requirements 4.5, 4.14, 4.16, D-34).
 *
 * <p>Initial status is PENDING.
 * Permitted transitions:
 * <ul>
 *   <li>PENDING -> CONFIRMED, CANCELLED</li>
 *   <li>CONFIRMED -> SHIPPED, CANCELLED</li>
 *   <li>SHIPPED -> DELIVERED, CANCELLED</li>
 *   <li>DELIVERED -> (terminal, none)</li>
 *   <li>CANCELLED -> (terminal, none)</li>
 * </ul>
 * Same-state transitions (e.g. PENDING -> PENDING) are illegal.
 */
@Component
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(OrderStatus.PENDING, Collections.unmodifiableSet(
                EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)));
        map.put(OrderStatus.CONFIRMED, Collections.unmodifiableSet(
                EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED)));
        map.put(OrderStatus.SHIPPED, Collections.unmodifiableSet(
                EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)));
        map.put(OrderStatus.DELIVERED, Collections.emptySet());
        map.put(OrderStatus.CANCELLED, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * Returns true if transition from {@code from} to {@code to} is allowed; false otherwise.
     * Same-state transitions return false.
     */
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return allowedFrom(from).contains(to);
    }

    /**
     * Returns the set of statuses reachable in one step from {@code from}.
     * Returns an empty set for terminal states (DELIVERED, CANCELLED) or null.
     */
    public Set<OrderStatus> allowedFrom(OrderStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return TRANSITIONS.getOrDefault(from, Collections.emptySet());
    }

    /**
     * Asserts that transitioning from {@code from} to {@code to} is valid, returning {@code to}.
     * Throws {@link InvalidStatusTransitionException} if the move is not permitted or is same-state.
     */
    public OrderStatus requireTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition order from " + from + " to " + to);
        }
        return to;
    }
}

