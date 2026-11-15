package com.mrsrik.ai_powered_commerce.ai.provider;

/**
 * Vendor-neutral contract for AI providers (Requirement 7.9).
 * Shields domain logic from specific LLM implementations (e.g. Google Gemini / Spring AI).
 */
public interface AiProvider {

    AiReply generateReply(AiRequest request) throws AiProviderException;
}
