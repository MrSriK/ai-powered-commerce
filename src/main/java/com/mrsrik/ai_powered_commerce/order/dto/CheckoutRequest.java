package com.mrsrik.ai_powered_commerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound checkout request carrying destination shipping details (Requirement 4.1, 4.8).
 */
public record CheckoutRequest(
        @NotNull @Valid ShippingAddressDto shippingAddress) {
}

