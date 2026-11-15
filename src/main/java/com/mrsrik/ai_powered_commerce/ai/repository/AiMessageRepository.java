package com.mrsrik.ai_powered_commerce.ai.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.ai.entity.AiMessage;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID conversationId);
}
