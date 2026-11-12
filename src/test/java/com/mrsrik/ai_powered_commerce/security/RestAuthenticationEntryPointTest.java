package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mrsrik.ai_powered_commerce.common.error.ErrorResponses;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestAuthenticationEntryPoint entryPoint =
            new RestAuthenticationEntryPoint(new ErrorResponses(CLOCK, MAPPER));

    private MockHttpServletResponse commence(AuthenticationException e) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(new MockHttpServletRequest(), response, e);
        return response;
    }

    private JsonNode body(MockHttpServletResponse r) throws IOException {
        return MAPPER.readTree(r.getContentAsString());
    }

    private void assertBody(MockHttpServletResponse r, int status, String code) throws IOException {
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getContentType()).startsWith("application/json");
        JsonNode json = body(r);
        assertThat(json.get("timestamp").asString()).isEqualTo("2025-01-02T03:04:05Z");
        assertThat(json.get("status").isNumber()).isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(status);
        assertThat(json.get("code").asString()).isEqualTo(code);
        assertThat(json.get("message").asString()).isNotBlank();
        assertThat(json.has("fieldErrors")).isFalse();
    }

    @Test
    void unauthenticatedGives401WithBearerChallenge() throws IOException {
        MockHttpServletResponse r = commence(new InsufficientAuthenticationException("none"));
        assertBody(r, 401, "AUTHENTICATION_REQUIRED");
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(r.getHeader("Retry-After")).isNull();
    }

    @Test
    void invalidTokenGives401() throws IOException {
        MockHttpServletResponse r = commence(new InvalidBearerTokenException("bad", new BadJwtException("x")));
        assertBody(r, 401, "INVALID_TOKEN");
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void accountDisabledGives401() throws IOException {
        MockHttpServletResponse r = commence(new AccountDisabledException());
        assertBody(r, 401, "ACCOUNT_DISABLED");
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void invalidApiKeyGives401() throws IOException {
        MockHttpServletResponse r = commence(new InvalidApiKeyException());
        assertBody(r, 401, "INVALID_API_KEY");
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void unreachableProviderGives503WithRetryAfterAndNoChallenge() throws IOException {
        MockHttpServletResponse r = commence(new AuthenticationServiceException("x", new JwtException("down")));
        assertBody(r, 503, "AUTH_TEMPORARILY_UNAVAILABLE");
        assertThat(r.getHeader("Retry-After")).isEqualTo("30");
        assertThat(r.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void failureHandlerDelegatesAndDoesNotRethrow() throws IOException {
        RestAuthenticationFailureHandler handler = new RestAuthenticationFailureHandler(entryPoint);
        MockHttpServletResponse r = new MockHttpServletResponse();
        assertThatCode(() -> handler.onAuthenticationFailure(new MockHttpServletRequest(), r,
                new AuthenticationServiceException("x", new JwtException("down")))).doesNotThrowAnyException();
        assertBody(r, 503, "AUTH_TEMPORARILY_UNAVAILABLE");
    }
}
