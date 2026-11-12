package com.mrsrik.ai_powered_commerce.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/** Builds the JWT decoder that validates Auth0 access tokens. */
@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfig {

    /** Allowed clock difference between this server and the issuer. */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    /**
     * The validation rules applied after the signature check: timestamps (60 s
     * skew), issuer, audience, subject.
     * The issuer is compared with the {@code iss} claim exactly, so Auth0 issuers
     * must keep their trailing slash.
     */
    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(issuer),
                new AudienceValidator(audience),
                new SubjectValidator());
    }

    /**
     * Lazy decoder: {@code withIssuerLocation(...).build()} contacts the issuer's
     * discovery endpoint immediately,
     * so it runs inside a {@link SupplierJwtDecoder}, i.e. on the first token, not
     * at startup. The application
     * therefore starts while Auth0 is unreachable. The signature algorithm (RS256)
     * comes from the discovery/JWKS.
     * <p>
     * The lazy decoder is wrapped in an
     * {@link IdentityProviderFailureTranslatingJwtDecoder}. When the issuer is
     * unreachable, {@code SupplierJwtDecoder} throws a
     * {@code JwtDecoderInitializationException}, which is a plain
     * {@code RuntimeException} and not a {@code JwtException}, so Spring's
     * {@code JwtAuthenticationProvider} would let it escape as an HTTP 500. The
     * decorator converts it into a plain {@code JwtException} (not a
     * {@code BadJwtException}) with the original as cause. The provider then wraps
     * it in an {@code AuthenticationServiceException} and the entry point answers
     * 503 AUTH_TEMPORARILY_UNAVAILABLE (Requirement 5.6, D-64). Token problems
     * ({@code BadJwtException}) still pass through and produce 401.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.issuer-uri}") String issuer,
            @Value("${app.auth.audience}") String audience) {
        return new IdentityProviderFailureTranslatingJwtDecoder(new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            decoder.setJwtValidator(tokenValidator(issuer, audience));
            return decoder;
        }));
    }
}
