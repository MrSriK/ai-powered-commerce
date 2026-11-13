package com.mrsrik.ai_powered_commerce.product.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record ProductDetailDto(
        UUID id,
        UUID categoryId,
        String name,
        String description,
        String brand,
        String sku,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        int stock,
        List<ProductImageDto> images) {

    public ProductDetailDto {
        if (costPrice != null) {
            costPrice = costPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (sellingPrice != null) {
            sellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
        }
        images = images == null ? List.of() : List.copyOf(images);
    }
}

