package com.mrsrik.ai_powered_commerce.order.service;

import java.util.UUID;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

/**
 * Executes a single order progression transition inside its own isolated transaction boundary (Requirements 12.2, 12.3, 12.9, 4.23).
 */
public interface OrderStepExecutor {

    /**
     * Attempts to advance an order from the expected status to the next progression status.
     * Runs with Propagation.REQUIRES_NEW.
     * Returns ADVANCED if successful, or SKIPPED if the order is missing or no longer in expectedFrom status.
     */
    StepOutcome advanceOne(UUID orderId, OrderStatus expectedFrom);
}
