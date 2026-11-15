package com.mrsrik.ai_powered_commerce.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Auth0 identity provider and client configuration properties (prefix {@code app.auth}).
 * Fast fails startup if any required property is missing or blank.
 * The client secret is masked in {@code toString()}.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank(message = "app.auth.audience must be set and not blank") String audience,
        @NotBlank(message = "app.auth.client-id must be set and not blank") String clientId,
        @NotBlank(message = "app.auth.client-secret must be set and not blank") String clientSecret,
        @NotBlank(message = "app.auth.domain must be set and not blank") String domain,
        @NotBlank(message = "app.auth.connection must be set and not blank") String connection) {

    @Override
    public String toString() {
        return "AuthProperties[audience=" + audience
                + ", clientId=****, clientSecret=****, domain=" + domain
                + ", connection=" + connection + "]";
    }
}
