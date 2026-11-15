package com.mrsrik.ai_powered_commerce.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0SignupResponse(
        @JsonProperty("_id") String id,
        String email,
        @JsonProperty("email_verified") boolean emailVerified
) {}
