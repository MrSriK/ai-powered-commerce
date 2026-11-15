package com.mrsrik.ai_powered_commerce.ai.provider;

import java.util.List;

import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;

/**
 * Encapsulates the complete context sent to an AI provider (Requirements 7.3, 7.9).
 * Contains system instruction, conversation history turns, user message, and catalog items.
 */
public record AiRequest(
        String systemInstruction,
        List<AiTurn> history,
        String userMessage,
        List<ProductSearchResult> catalog) {

    public AiRequest {
        history = history == null ? List.of() : List.copyOf(history);
        catalog = catalog == null ? List.of() : List.copyOf(catalog);
    }
}
