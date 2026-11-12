package com.mrsrik.ai_powered_commerce.security;

import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.nimbusds.jose.RemoteKeySourceException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Maps an authentication failure (and its cause chain) to an HTTP status, error code and a
 * safe message. Rows are evaluated in the order of the design table. Messages never echo
 * tokens, claims, URLs or internal exception text.
 */
public final class AuthFailureClassifier {

    /** The classified outcome. */
    public record Failure(int status, ErrorCode code, String message) {
    }

    private static final Failure ACCOUNT_DISABLED =
            new Failure(401, ErrorCode.ACCOUNT_DISABLED, "The account is disabled");
    private static final Failure INVALID_API_KEY =
            new Failure(401, ErrorCode.INVALID_API_KEY, "Invalid API key");
    private static final Failure UNAVAILABLE = new Failure(
            503, ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE, "Authentication service is temporarily unavailable");
    private static final Failure IDENTITY_UNRESOLVABLE =
            new Failure(401, ErrorCode.IDENTITY_UNRESOLVABLE, "The token does not identify a user");
    private static final Failure INVALID_TOKEN =
            new Failure(401, ErrorCode.INVALID_TOKEN, "The access token is invalid");
    private static final Failure REQUIRED =
            new Failure(401, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required");

    private AuthFailureClassifier() {
    }

    /** Classifies the failure; never returns null and always terminates, even on cyclic cause chains. */
    public static Failure classify(Throwable failure) {
        List<Throwable> chain = chainOf(failure);

        if (failure instanceof AccountDisabledException) {
            return ACCOUNT_DISABLED;
        }
        if (failure instanceof InvalidApiKeyException) {
            return INVALID_API_KEY;
        }
        if (chain.stream().anyMatch(AuthFailureClassifier::isIdentityProviderOutage)) {
            return UNAVAILABLE;
        }
        if (chain.stream().anyMatch(AuthFailureClassifier::isUnresolvableSubject)) {
            return IDENTITY_UNRESOLVABLE;
        }
        if (failure instanceof OAuth2AuthenticationException) {
            return INVALID_TOKEN;
        }
        return REQUIRED;
    }

    private static boolean isIdentityProviderOutage(Throwable t) {
        return (t instanceof JwtException && !(t instanceof BadJwtException))
                || t instanceof RemoteKeySourceException
                || t instanceof ResourceAccessException
                || t instanceof IOException;
    }

    private static boolean isUnresolvableSubject(Throwable t) {
        return t instanceof BadJwtException
                && t.getMessage() != null
                && t.getMessage().contains(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
    }

    /** The throwable followed by its causes, each visited at most once (identity based). */
    private static List<Throwable> chainOf(Throwable start) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = start; t != null && seen.add(t); t = t.getCause()) {
            chain.add(t);
        }
        return chain;
    }
}
