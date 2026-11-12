package com.mrsrik.ai_powered_commerce.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the internal endpoints. A blank key fails startup, so the internal chain
 * can never run with an empty secret. {@code toString} hides the key.
 *
 * @param apiKey shared secret expected in the {@code X-Internal-Api-Key} header
 */
@Validated
@ConfigurationProperties(prefix = "app.internal")
public record InternalApiKeyProperties(@NotBlank(message = "app.internal.api-key must be set and not blank") String apiKey) {

    @Override
    public String toString() {
        return "InternalApiKeyProperties[apiKey=****]";
    }
}
