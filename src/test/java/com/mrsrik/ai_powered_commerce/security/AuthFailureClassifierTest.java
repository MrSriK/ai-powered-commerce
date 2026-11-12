package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.mrsrik.ai_powered_commerce.security.AuthFailureClassifier.Failure;
import com.nimbusds.jose.RemoteKeySourceException;
import java.io.IOException;
import java.net.BindException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.client.ResourceAccessException;

class AuthFailureClassifierTest {

    private static void assertFailure(Throwable t, int status, ErrorCode code) {
        Failure f = AuthFailureClassifier.classify(t);
        assertThat(f.status()).isEqualTo(status);
        assertThat(f.code()).isEqualTo(code);
        assertThat(f.message()).isNotBlank();
    }

    @Test
    void accountDisabled() {
        assertFailure(new AccountDisabledException(), 401, ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void invalidApiKey() {
        assertFailure(new InvalidApiKeyException(), 401, ErrorCode.INVALID_API_KEY);
    }

    @Test
    void unreachableIdentityProviderChainIs503() {
        ResourceAccessException rae = new ResourceAccessException("I/O error", new BindException("refused"));
        IllegalArgumentException iae = new IllegalArgumentException("discovery failed", rae);
        JwtDecoderInitializationException init = new JwtDecoderInitializationException("init", iae);
        JwtException jwt = new JwtException("Unable to reach the identity provider", init);
        assertFailure(new AuthenticationServiceException("x", jwt), 503, ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void remoteKeySourceExceptionOnlyIs503() {
        Throwable t = new AuthenticationServiceException("x",
                new RuntimeException(new RemoteKeySourceException("k", null)));
        assertFailure(t, 503, ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void ioExceptionOnlyIs503() {
        Throwable t = new AuthenticationServiceException("x", new IOException("reset"));
        assertFailure(t, 503, ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void plainJwtExceptionIs503() {
        assertFailure(new AuthenticationServiceException("x", new JwtException("boom")), 503,
                ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void badJwtWithoutMarkerIsInvalidTokenNot503() {
        BadJwtException bad = new BadJwtException("An error occurred while attempting to decode the Jwt: expired");
        assertFailure(new InvalidBearerTokenException("bad", bad), 401, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void badJwtWithMarkerIsIdentityUnresolvable() {
        BadJwtException bad = new BadJwtException(
                "An error occurred while attempting to decode the Jwt: "
                        + SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
        assertFailure(new InvalidBearerTokenException("bad", bad), 401, ErrorCode.IDENTITY_UNRESOLVABLE);
    }

    @Test
    void otherOAuth2AuthenticationExceptionIsInvalidToken() {
        assertFailure(new OAuth2AuthenticationException(new OAuth2Error("invalid_token")), 401,
                ErrorCode.INVALID_TOKEN);
    }

    @Test
    void noCredentialsIsAuthenticationRequired() {
        assertFailure(new InsufficientAuthenticationException("none"), 401, ErrorCode.AUTHENTICATION_REQUIRED);
        assertFailure(new BadCredentialsException("bad"), 401, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void unrelatedServiceFailureIsNot503() {
        Throwable t = new AuthenticationServiceException("x", new IllegalStateException("db down"));
        assertFailure(t, 401, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void ownExceptionsWinOverCauses() {
        AccountDisabledException disabled = new AccountDisabledException();
        disabled.initCause(new IOException("x"));
        assertFailure(disabled, 401, ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void cyclicCauseChainTerminates() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertFailure(new AuthenticationServiceException("x", a), 401, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void messagesDoNotEchoInternalText() {
        Failure f = AuthFailureClassifier.classify(
                new InvalidBearerTokenException("secret-token-abc", new BadJwtException("eyJ.secret")));
        assertThat(f.message()).doesNotContain("secret");
    }
}
