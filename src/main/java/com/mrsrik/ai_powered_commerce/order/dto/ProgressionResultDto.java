package com.mrsrik.ai_powered_commerce.order.dto;

/**
 * Result counts from an automated order progression cycle (Requirements 12.6, 12.7).
 */
public record ProgressionResultDto(
        int confirmed,
        int shipped,
        int delivered,
        int failed) {
}
