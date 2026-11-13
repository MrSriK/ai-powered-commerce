package com.mrsrik.ai_powered_commerce.cart.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(1) @Max(999) Integer quantity) {
}

