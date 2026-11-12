package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private static final String EXPECTED = "commerce-api-secret-audience";

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> c.putAll(claims)).build();
    }

    @Test
    void succeedsWhenAudienceIsAmongSeveral() {
        Jwt jwt = jwt(Map.of("sub", "u1", "aud", List.of("other", EXPECTED, "third")));

        assertThat(new AudienceValidator(EXPECTED).validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void failsWhenAudienceIsAbsent() {
        Jwt jwt = jwt(Map.of("sub", "u1", "aud", List.of("other")));

        assertInvalidToken(new AudienceValidator(EXPECTED).validate(jwt));
    }

    @Test
    void failsWhenAudienceClaimIsMissing() {
        Jwt jwt = jwt(Map.of("sub", "u1"));

        assertInvalidToken(new AudienceValidator(EXPECTED).validate(jwt));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t "})
    void rejectsNullOrBlankExpectedAudience(String expected) {
        assertThatThrownBy(() -> new AudienceValidator(expected)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInvalidToken(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        OAuth2Error error = result.getErrors().iterator().next();
        assertThat(error.getErrorCode()).isEqualTo("invalid_token");
        assertThat(error.getDescription()).isEqualTo("The required audience is missing").doesNotContain(EXPECTED);
    }
}
