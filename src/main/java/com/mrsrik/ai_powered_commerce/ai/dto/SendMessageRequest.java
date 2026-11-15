package com.mrsrik.ai_powered_commerce.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for sending a chat message to an AI conversation (Requirements 7.2, 7.7).
 * Uses @NotBlank and deliberately has no upper length cap (D-30).
 */
public record SendMessageRequest(
        @NotBlank String message) {
}
