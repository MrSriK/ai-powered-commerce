package com.mrsrik.ai_powered_commerce.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String description,
        @NotBlank @Size(max = 100) String brand,
        @NotBlank @Size(max = 255) String sku,
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
        @NotNull @Min(0) Integer stock,
        Boolean active) {

    public UpdateProductRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
        brand = brand == null ? null : brand.trim();
        sku = sku == null ? null : sku.trim();
    }
}

