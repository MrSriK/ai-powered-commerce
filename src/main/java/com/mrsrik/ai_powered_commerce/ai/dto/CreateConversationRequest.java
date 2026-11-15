package com.mrsrik.ai_powered_commerce.ai.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new AI conversation (Requirement 7.1).
 */
public record CreateConversationRequest(
        @Size(max = 255) String title) {
}
