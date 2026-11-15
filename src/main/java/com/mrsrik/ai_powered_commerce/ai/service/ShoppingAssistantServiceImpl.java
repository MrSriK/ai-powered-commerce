package com.mrsrik.ai_powered_commerce.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.ai.dto.AiMessageDto;
import com.mrsrik.ai_powered_commerce.ai.dto.ConversationDto;
import com.mrsrik.ai_powered_commerce.ai.dto.CreateConversationRequest;
import com.mrsrik.ai_powered_commerce.ai.dto.SendMessageRequest;
import com.mrsrik.ai_powered_commerce.ai.entity.AiConversation;
import com.mrsrik.ai_powered_commerce.ai.entity.AiMessage;
import com.mrsrik.ai_powered_commerce.ai.provider.AiProvider;
import com.mrsrik.ai_powered_commerce.ai.provider.AiProviderException;
import com.mrsrik.ai_powered_commerce.ai.provider.AiReply;
import com.mrsrik.ai_powered_commerce.ai.provider.AiRequest;
import com.mrsrik.ai_powered_commerce.ai.provider.AiTurn;
import com.mrsrik.ai_powered_commerce.ai.repository.AiConversationRepository;
import com.mrsrik.ai_powered_commerce.ai.repository.AiMessageRepository;
import com.mrsrik.ai_powered_commerce.common.error.ConversationNotFoundException;
import com.mrsrik.ai_powered_commerce.common.error.EmptyMessageException;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;
import com.mrsrik.ai_powered_commerce.product.service.ProductSearchService;

import jakarta.persistence.EntityManager;

/**
 * Implementation of {@link ShoppingAssistantService} (Requirement 7).
 *
 * <p>Key architectural guarantees:
 * <ul>
 *   <li>Owner-scoped isolation: access to another user's or non-existent conversation throws 404 (Requirement 7.6).</li>
 *   <li>The AI provider call is executed <b>outside any database transaction</b> on a virtual thread
 *       with a timeout (default 30 seconds), protecting database connection pools (Requirement 7.2, 7.8).</li>
 *   <li>Failures or timeouts throw {@link AiProviderException} (HTTP 502) without persisting anything (Requirement 7.8).</li>
 *   <li>Catalog querying uses read-only {@link ProductSearchService} exclusively (Requirements 7.3, 7.10).</li>
 * </ul>
 */
@Service
public class ShoppingAssistantServiceImpl implements ShoppingAssistantService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiMessageWriter aiMessageWriter;
    private final ProductSearchService productSearchService;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final EntityManager entityManager;
    private final Duration timeout;
    private final int maxProducts;
    private final ExecutorService executor;

    @org.springframework.beans.factory.annotation.Autowired
    public ShoppingAssistantServiceImpl(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AiMessageWriter aiMessageWriter,
            ProductSearchService productSearchService,
            PromptBuilder promptBuilder,
            AiProvider aiProvider,
            EntityManager entityManager,
            @Value("${app.ai.timeout:30s}") Duration timeout,
            @Value("${app.ai.max-products:50}") int maxProducts) {
        this(
                conversationRepository,
                messageRepository,
                aiMessageWriter,
                productSearchService,
                promptBuilder,
                aiProvider,
                entityManager,
                timeout,
                maxProducts,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    public ShoppingAssistantServiceImpl(
            AiConversationRepository conversationRepository,
            AiMessageRepository messageRepository,
            AiMessageWriter aiMessageWriter,
            ProductSearchService productSearchService,
            PromptBuilder promptBuilder,
            AiProvider aiProvider,
            EntityManager entityManager,
            Duration timeout,
            int maxProducts,
            ExecutorService executor) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiMessageWriter = aiMessageWriter;
        this.productSearchService = productSearchService;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.entityManager = entityManager;
        this.timeout = timeout;
        this.maxProducts = maxProducts;
        this.executor = executor;
    }

    @Transactional
    @Override
    public ConversationDto startConversation(UUID userId, CreateConversationRequest request) {
        String title = request != null ? request.title() : null;
        AiConversation conversation = AiConversation.builder()
                .userId(userId)
                .title(title)
                .build();
        AiConversation saved = conversationRepository.saveAndFlush(conversation);
        entityManager.refresh(saved);
        return new ConversationDto(saved.getId(), saved.getTitle(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    @Override
    public List<ConversationDto> listConversations(UUID userId) {
        return conversationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId)
                .stream()
                .map(c -> new ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<AiMessageDto> history(UUID userId, UUID conversationId) {
        conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);

        return messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(m -> new AiMessageDto(
                        m.getId(),
                        m.getConversationId(),
                        m.getUserInput(),
                        m.getAiOutput(),
                        m.getCreatedAt()))
                .toList();
    }

    @Override
    public AiMessageDto sendMessage(UUID userId, UUID conversationId, SendMessageRequest request) {
        // 1. Re-check blank input (Requirement 7.7)
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new EmptyMessageException();
        }
        String userMessage = request.message();

        // 2. Short read-only lookup of conversation and history (Requirements 7.4, 7.6)
        conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);

        List<AiMessage> previousMessages = messageRepository
                .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(conversationId);
        List<AiTurn> turns = previousMessages.stream()
                .map(m -> new AiTurn(m.getUserInput(), m.getAiOutput()))
                .toList();

        // 3. Obtain active products via read-only ProductSearchService (Requirements 7.3, 7.10)
        List<ProductSearchResult> activeProducts = productSearchService.findActiveProducts(maxProducts);

        // 4. PromptBuilder creates AiRequest (Requirement 7.3)
        AiRequest aiRequest = promptBuilder.buildRequest(activeProducts, turns, userMessage);

        // 5. Call AI Provider outside any transaction with timeout on virtual thread (Requirements 7.2, 7.8)
        AiReply reply = invokeProviderWithTimeout(aiRequest);
        if (reply == null || reply.text() == null) {
            throw new AiProviderException("AI provider returned an empty reply");
        }

        // 6. Second short transaction inserts AiMessage (Requirement 7.2)
        return aiMessageWriter.saveMessage(conversationId, userMessage, reply.text());
    }

    private AiReply invokeProviderWithTimeout(AiRequest request) {
        try {
            return CompletableFuture.supplyAsync(() -> aiProvider.generateReply(request), executor)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof AiProviderException ape) {
                throw ape;
            }
            if (cause instanceof TimeoutException) {
                throw new AiProviderException("AI provider timed out after " + timeout.toSeconds() + " seconds", cause);
            }
            throw new AiProviderException("AI provider call failed: " + cause.getMessage(), cause);
        } catch (AiProviderException ape) {
            throw ape;
        } catch (Exception e) {
            throw new AiProviderException("AI provider invocation failed", e);
        }
    }
}
