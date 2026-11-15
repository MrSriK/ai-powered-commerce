package com.mrsrik.ai_powered_commerce.ai.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.ai.dto.AiMessageDto;
import com.mrsrik.ai_powered_commerce.ai.entity.AiMessage;
import com.mrsrik.ai_powered_commerce.ai.repository.AiMessageRepository;

import jakarta.persistence.EntityManager;

/**
 * Isolated transactional writer for persisting AI messages (Requirement 7.2).
 * Runs in its own short transaction after the AI provider call completes.
 */
@Component
public class AiMessageWriter {

    private final AiMessageRepository aiMessageRepository;
    private final EntityManager entityManager;

    public AiMessageWriter(AiMessageRepository aiMessageRepository, EntityManager entityManager) {
        this.aiMessageRepository = aiMessageRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AiMessageDto saveMessage(UUID conversationId, String userInput, String aiOutput) {
        AiMessage message = AiMessage.builder()
                .conversationId(conversationId)
                .userInput(userInput)
                .aiOutput(aiOutput)
                .build();
        AiMessage saved = aiMessageRepository.saveAndFlush(message);
        entityManager.refresh(saved);
        return new AiMessageDto(
                saved.getId(),
                saved.getConversationId(),
                saved.getUserInput(),
                saved.getAiOutput(),
                saved.getCreatedAt());
    }
}
