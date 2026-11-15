package com.mrsrik.ai_powered_commerce.ai.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of an exchange (turn) in an AI conversation (Requirements 7.2, 7.4).
 */
public record AiMessageDto(
        UUID id,
        UUID conversationId,
        String userInput,
        String aiOutput,
        Instant createdAt) {
}
