package com.mrsrik.ai_powered_commerce.product.dto;

import java.util.UUID;

public record ProductImageDto(
        UUID id,
        int displayOrder,
        String imageUrl) {
}

