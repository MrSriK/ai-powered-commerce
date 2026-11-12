package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Failure modes of the decoder bean: unreachable issuer, recovery and the
 * provider mapping.
 */
class JwtDecoderFailureModesTest {

    private static final String UNREACHABLE_ISSUER = "http://localhost:0/issuer-placeholder/";
    private static final String ISSUER = "https://example.test/";
    private static final String AUDIENCE = "https://api.example.test";

    private static KeyPair keys;
    private static KeyPair otherKeys;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private static String token(KeyPair signer) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("auth0|abc")
                .audience(AUDIENCE)
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    private static JwtDecoder localDecoder() {
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        nimbus.setJwtValidator(JwtDecoderConfig.tokenValidator(ISSUER, AUDIENCE));
        return nimbus;
    }

    private static JwtDecoder unreachableBean() {
        return new JwtDecoderConfig().jwtDecoder(UNREACHABLE_ISSUER, AUDIENCE);
    }

    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append("\n  ").append(c.getClass().getName());
        }
        return sb.toString();
    }

    private static boolean chainContains(Throwable t, Class<?> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void unreachableIssuerThrowsPlainJwtExceptionWithOriginalCause() {
        JwtDecoder bean = unreachableBean();
        JwtException e = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(JwtException.class, () -> bean.decode("a.b.c")));
        System.out.println("DECORATED CHAIN:" + chain(e));
        assertThat(e).isNotInstanceOf(BadJwtException.class);
        assertThat(e.getMessage()).isEqualTo(IdentityProviderFailureTranslatingJwtDecoder.MESSAGE);
        assertThat(e.getCause()).isInstanceOf(JwtDecoderInitializationException.class);
        assertThat(chainContains(e, ResourceAccessException.class)).isTrue();
    }

    @Test
    void badTokenStillGivesBadJwtException() throws Exception {
        JwtDecoder decorated = new IdentityProviderFailureTranslatingJwtDecoder(localDecoder());
        String bad = token(otherKeys);
        assertThrows(BadJwtException.class, () -> decorated.decode(bad));
        assertThrows(BadJwtException.class, () -> decorated.decode("not-a-jwt"));
    }

    @Test
    void supplierRetriesAfterFailureAndRecovers() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        JwtDecoder decorated = new IdentityProviderFailureTranslatingJwtDecoder(new SupplierJwtDecoder(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("issuer down");
            }
            return localDecoder();
        }));
        String good = token(keys);

        JwtException first = assertThrows(JwtException.class, () -> decorated.decode(good));
        assertThat(first).isNotInstanceOf(BadJwtException.class);
        Jwt second = decorated.decode(good);
        assertThat(second.getSubject()).isEqualTo("auth0|abc");
        assertThat(decorated.decode(good).getSubject()).isEqualTo("auth0|abc");
        System.out.println("SUPPLIER CALLS after fail, success, success = " + calls.get());
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void providerMapsUnreachableIssuerToAuthenticationServiceException() {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(unreachableBean());
        provider.setJwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt));
        AuthenticationServiceException e = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(AuthenticationServiceException.class,
                        () -> provider.authenticate(new BearerTokenAuthenticationToken("a.b.c"))));
        System.out.println("PROVIDER CHAIN:" + chain(e));
        assertThat(chainContains(e, ResourceAccessException.class)).isTrue();
        assertThat(chainContains(e, IOException.class)).isTrue();
    }

    @Test
    void providerMapsBadSignatureToInvalidBearerToken() throws Exception {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(
                new IdentityProviderFailureTranslatingJwtDecoder(localDecoder()));
        provider.setJwtAuthenticationConverter(jwt -> new JwtAuthenticationToken(jwt));
        String bad = token(otherKeys);
        assertThrows(InvalidBearerTokenException.class,
                () -> provider.authenticate(new BearerTokenAuthenticationToken(bad)));
    }
}
