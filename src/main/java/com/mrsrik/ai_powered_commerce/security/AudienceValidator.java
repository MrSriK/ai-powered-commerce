package com.mrsrik.ai_powered_commerce.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Accepts a JWT only when its {@code aud} claim contains the configured audience. */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error AUDIENCE_MISSING = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The required audience is missing", null);

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("The expected audience must not be null or blank");
        }
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience != null && audience.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(AUDIENCE_MISSING);
    }
}
