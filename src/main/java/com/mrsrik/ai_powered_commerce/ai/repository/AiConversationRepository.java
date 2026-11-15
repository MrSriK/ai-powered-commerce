package com.mrsrik.ai_powered_commerce.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.ai.entity.AiConversation;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID userId);

    Optional<AiConversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
