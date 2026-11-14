package com.mrsrik.ai_powered_commerce.order.dto;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Admin request to update an order's status (Requirement 4.14).
 */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status) {
}

