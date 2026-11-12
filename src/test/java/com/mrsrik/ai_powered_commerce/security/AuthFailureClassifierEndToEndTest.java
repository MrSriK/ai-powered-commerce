package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.mrsrik.ai_powered_commerce.security.AuthFailureClassifier.Failure;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

/**
 * Whole path: real NimbusJwtDecoder with the production validators, real
 * JwtAuthenticationProvider, real exception, then AuthFailureClassifier.
 */
class AuthFailureClassifierEndToEndTest {

    private static final String UNREACHABLE_ISSUER = "http://localhost:0/issuer-placeholder/";
    private static final String ISSUER = "https://example.test/";
    private static final String AUDIENCE = "https://test-api.example.test";

    private static KeyPair keys;
    private static KeyPair otherKeys;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private static String token(KeyPair signer, String issuer, String audience, String subject, long expiresInSeconds)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().plusSeconds(expiresInSeconds)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    private static String valid(String subject) throws Exception {
        return token(keys, ISSUER, AUDIENCE, subject, 300);
    }

    private static JwtAuthenticationProvider localProvider() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(JwtDecoderConfig.tokenValidator(ISSUER, AUDIENCE));
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        provider.setJwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt, java.util.List.of()));
        return provider;
    }

    private static AuthenticationException failureOf(String token) {
        JwtAuthenticationProvider provider = localProvider();
        return assertThrows(AuthenticationException.class,
                () -> provider.authenticate(new BearerTokenAuthenticationToken(token)));
    }

    private static void assertClassified(AuthenticationException e, int status, ErrorCode code) {
        Failure f = AuthFailureClassifier.classify(e);
        assertThat(f.status()).isEqualTo(status);
        assertThat(f.code()).isEqualTo(code);
    }

    @Test
    void emptySubjectIsIdentityUnresolvable() throws Exception {
        AuthenticationException e = failureOf(valid(""));
        System.out.println("EMPTY SUB MESSAGE: " + e.getMessage());
        assertThat(e.getMessage()).contains(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
        assertClassified(e, 401, ErrorCode.IDENTITY_UNRESOLVABLE);
    }

    @Test
    void whitespaceSubjectIsIdentityUnresolvable() throws Exception {
        AuthenticationException e = failureOf(valid("   "));
        assertThat(e.getMessage()).contains(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
        assertClassified(e, 401, ErrorCode.IDENTITY_UNRESOLVABLE);
    }

    @Test
    void badSignatureIsInvalidToken() throws Exception {
        AuthenticationException e = failureOf(token(otherKeys, ISSUER, AUDIENCE, "auth0|abc", 300));
        System.out.println("BAD SIGNATURE MESSAGE: " + e.getMessage());
        assertThat(e.getMessage()).doesNotContain(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
        assertClassified(e, 401, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void expiredTokenIsInvalidToken() throws Exception {
        assertClassified(failureOf(token(keys, ISSUER, AUDIENCE, "auth0|abc", -120)), 401, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void wrongAudienceIsInvalidToken() throws Exception {
        assertClassified(failureOf(token(keys, ISSUER, "https://other.example.test", "auth0|abc", 300)),
                401, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void wrongIssuerIsInvalidToken() throws Exception {
        assertClassified(failureOf(token(keys, "https://evil.example.test/", AUDIENCE, "auth0|abc", 300)),
                401, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        Authentication result = localProvider().authenticate(new BearerTokenAuthenticationToken(valid("auth0|abc")));
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void unreachableIdentityProviderIs503() {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(
                new JwtDecoderConfig().jwtDecoder(UNREACHABLE_ISSUER, AUDIENCE));
        provider.setJwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt, java.util.List.of()));
        AuthenticationServiceException e = assertThrows(AuthenticationServiceException.class,
                () -> provider.authenticate(new BearerTokenAuthenticationToken("a.b.c")));
        assertClassified(e, 503, ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE);
    }
}
