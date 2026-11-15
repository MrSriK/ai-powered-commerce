package com.mrsrik.ai_powered_commerce.ai.service;

import java.util.List;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.ai.dto.AiMessageDto;
import com.mrsrik.ai_powered_commerce.ai.dto.ConversationDto;
import com.mrsrik.ai_powered_commerce.ai.dto.CreateConversationRequest;
import com.mrsrik.ai_powered_commerce.ai.dto.SendMessageRequest;

/**
 * Service orchestrating AI shopping assistant conversations, catalog retrieval,
 * and AI provider interactions (Requirement 7).
 */
public interface ShoppingAssistantService {

    /** Starts a new conversation for the authenticated user (Requirement 7.1). */
    ConversationDto startConversation(UUID userId, CreateConversationRequest request);

    /** Lists all conversations owned by the user, newest first (Requirement 7.5). */
    List<ConversationDto> listConversations(UUID userId);

    /**
     * Sends a message in a conversation, invokes the AI provider outside of any transaction,
     * and records the resulting exchange (Requirements 7.2, 7.3, 7.6, 7.7, 7.8, 7.10).
     */
    AiMessageDto sendMessage(UUID userId, UUID conversationId, SendMessageRequest request);

    /** Retrieves the chronological message history of an owned conversation (Requirements 7.4, 7.6). */
    List<AiMessageDto> history(UUID userId, UUID conversationId);
}
