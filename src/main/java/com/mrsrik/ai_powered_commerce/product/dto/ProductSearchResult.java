package com.mrsrik.ai_powered_commerce.product.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Read-only product representation tailored for the AI assistant (Requirements 7.3, 7.10).
 * Deliberately excludes costPrice so internal margins are never passed to the LLM (D-85).
 */
public record ProductSearchResult(
        UUID id,
        String name,
        String brand,
        String categoryName,
        BigDecimal sellingPrice,
        String description) {

    public ProductSearchResult {
        if (sellingPrice != null) {
            sellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

