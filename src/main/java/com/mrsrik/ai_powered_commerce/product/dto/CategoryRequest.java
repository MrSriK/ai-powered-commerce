package com.mrsrik.ai_powered_commerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name) {

    public CategoryRequest {
        name = name == null ? null : name.trim();
    }
}

