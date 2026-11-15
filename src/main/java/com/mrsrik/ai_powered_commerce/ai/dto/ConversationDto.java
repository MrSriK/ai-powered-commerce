package com.mrsrik.ai_powered_commerce.ai.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of an AI conversation (Requirement 7.1, 7.5).
 */
public record ConversationDto(
        UUID id,
        String title,
        Instant createdAt) {
}
