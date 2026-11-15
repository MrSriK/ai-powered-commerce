package com.mrsrik.ai_powered_commerce.ai.provider;

/**
 * Represents a single turn in a conversation history (Requirement 7.3, 7.9).
 */
public record AiTurn(String userInput, String aiOutput) {
}
