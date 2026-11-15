package com.mrsrik.ai_powered_commerce.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0TokenRequest(
        @JsonProperty("client_id") String clientId,
        String username,
        String password,
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("client_secret") String clientSecret,
        String audience
) {}
