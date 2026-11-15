package com.mrsrik.ai_powered_commerce.ai.provider;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

/**
 * Factory and configuration that registers the active {@link AiProvider} bean based on
 * {@code app.ai.provider} (Requirements 7.2, 7.8, 7.9).
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderFactory {

    /**
     * Configures the Google GenAI {@link Client} with HTTP call timeout aligned with the service limit (Requirement 7.2, 7.8).
     */
    @Bean
    @ConditionalOnMissingBean
    public Client googleGenAiClient(
            GoogleGenAiConnectionProperties properties,
            AiProviderProperties aiProperties) {
        Client.Builder builder = Client.builder();
        if (properties.isVertexAi()
                || (StringUtils.hasText(properties.getProjectId()) && StringUtils.hasText(properties.getLocation()))) {
            builder.vertexAI(true);
            if (StringUtils.hasText(properties.getProjectId())) {
                builder.project(properties.getProjectId());
            }
            if (StringUtils.hasText(properties.getLocation())) {
                builder.location(properties.getLocation());
            }
        } else if (StringUtils.hasText(properties.getApiKey())) {
            builder.apiKey(properties.getApiKey());
        }

        Duration timeout = aiProperties.getTimeout() != null ? aiProperties.getTimeout() : Duration.ofSeconds(30);
        builder.httpOptions(HttpOptions.builder()
                .timeout(Math.toIntExact(timeout.toMillis()))
                .build());

        return builder.build();
    }

    /**
     * Registers {@link SpringAiProvider} when {@code app.ai.provider} is "spring-ai" (the default).
     */
    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "spring-ai", matchIfMissing = true)
    public AiProvider springAiProvider(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ChatModel chatModel) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        ChatClient chatClient = builder != null ? builder.build() : ChatClient.create(chatModel);
        return new SpringAiProvider(chatClient);
    }
}
