package com.mrsrik.ai_powered_commerce.ai.provider;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Concrete {@link AiProvider} implementation backed by Spring AI and Google Gemini (Requirements 7.2, 7.8, 7.9).
 *
 * <p>Builds a {@link ChatClient} prompt containing system instruction (with delimited catalog block),
 * conversation history turns, and latest user message. Wraps any provider exceptions or empty replies
 * in {@link AiProviderException}.
 */
public class SpringAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiProvider.class);

    private final ChatClient chatClient;

    public SpringAiProvider(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public SpringAiProvider(ChatModel chatModel) {
        this(ChatClient.create(chatModel));
    }

    @Override
    public AiReply generateReply(AiRequest request) throws AiProviderException {
        try {
            List<Message> messages = new ArrayList<>();

            // 1. Conversation history turns
            if (request.history() != null) {
                for (AiTurn turn : request.history()) {
                    if (turn.userInput() != null && !turn.userInput().isBlank()) {
                        messages.add(new UserMessage(turn.userInput()));
                    }
                    if (turn.aiOutput() != null && !turn.aiOutput().isBlank()) {
                        messages.add(new AssistantMessage(turn.aiOutput()));
                    }
                }
            }

            // 2. Latest user message
            if (request.userMessage() != null && !request.userMessage().isBlank()) {
                messages.add(new UserMessage(request.userMessage()));
            }

            ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

            // 3. System instruction (includes persona and delimited catalog data)
            if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
                spec = spec.system(request.systemInstruction());
            }

            if (!messages.isEmpty()) {
                spec = spec.messages(messages);
            }

            String content = spec.call().content();

            if (content == null || content.isBlank()) {
                throw new AiProviderException("AI provider returned an empty reply");
            }

            return new AiReply(content);
        } catch (AiProviderException ape) {
            throw ape;
        } catch (Exception e) {
            log.warn("Spring AI invocation failed: {}", e.getMessage(), e);
            throw new AiProviderException("AI provider failed to generate a reply: " + e.getMessage(), e);
        }
    }
}
