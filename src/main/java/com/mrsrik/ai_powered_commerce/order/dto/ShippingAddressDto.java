package com.mrsrik.ai_powered_commerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Snapshot of customer shipping address attributes for orders (Requirement 4.2).
 */
public record ShippingAddressDto(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 15) String phoneNumber,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country) {
}

