package com.mrsrik.ai_powered_commerce.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0SignupRequest(
        @JsonProperty("client_id") String clientId,
        String email,
        String password,
        String connection,
        String name
) {}
