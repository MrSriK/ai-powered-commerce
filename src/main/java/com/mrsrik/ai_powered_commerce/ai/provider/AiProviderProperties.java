package com.mrsrik.ai_powered_commerce.ai.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI shopping assistant and provider (Requirements 7.2, 7.8, 7.9).
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {

    /**
     * Provider implementation to select ("spring-ai" or "fake").
     */
    private String provider = "spring-ai";

    /**
     * Timeout for AI service and HTTP client calls (default 30 seconds).
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Maximum number of active products injected into the prompt catalog (default 50).
     */
    private int maxProducts = 50;

    /**
     * Maximum number of past turns included in conversation history (default 10).
     */
    private int maxHistoryTurns = 10;

    /**
     * Configured LLM model name.
     */
    private String model;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxProducts() {
        return maxProducts;
    }

    public void setMaxProducts(int maxProducts) {
        this.maxProducts = maxProducts;
    }

    public int getMaxHistoryTurns() {
        return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(int maxHistoryTurns) {
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
