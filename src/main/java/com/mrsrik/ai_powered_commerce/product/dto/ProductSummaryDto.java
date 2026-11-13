package com.mrsrik.ai_powered_commerce.product.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ProductSummaryDto(
        UUID id,
        UUID categoryId,
        String name,
        String brand,
        String sku,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        int stock) {

    public ProductSummaryDto {
        if (costPrice != null) {
            costPrice = costPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (sellingPrice != null) {
            sellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

