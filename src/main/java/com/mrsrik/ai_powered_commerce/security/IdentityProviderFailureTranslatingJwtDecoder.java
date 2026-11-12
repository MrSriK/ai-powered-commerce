package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Decorator that turns "could not reach the identity provider" failures into a
 * plain {@link JwtException} (never a {@link BadJwtException}).
 * <p>
 * The lazy {@code SupplierJwtDecoder} throws {@code JwtDecoderInitializationException}
 * when the issuer cannot be resolved, and that type extends
 * {@link RuntimeException}, not {@link JwtException}. The resource-server
 * {@code JwtAuthenticationProvider} only handles {@code BadJwtException} (401) and
 * {@code JwtException} (wrapped in {@code AuthenticationServiceException}), so an
 * untranslated failure would surface as an HTTP 500. With this decorator the
 * provider wraps it and the entry point classifies the cause chain as 503.
 * The original exception stays reachable through {@link Throwable#getCause()}.
 */
final class IdentityProviderFailureTranslatingJwtDecoder implements JwtDecoder {

    /** Fixed generic message: it never contains the issuer URL or the token. */
    static final String MESSAGE = "Unable to reach the identity provider";

    private final JwtDecoder delegate;

    IdentityProviderFailureTranslatingJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return delegate.decode(token);
        } catch (JwtException e) {
            // Includes BadJwtException: token problems pass through unchanged.
            throw e;
        } catch (RuntimeException e) {
            throw new JwtException(MESSAGE, e);
        }
    }
}
