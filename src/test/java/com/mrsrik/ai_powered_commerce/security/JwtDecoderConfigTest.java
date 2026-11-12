package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class JwtDecoderConfigTest {

    private static final String ISSUER = "https://example.test/";
    private static final String AUDIENCE = "https://api.example.test";

    private static KeyPair keys;
    private static KeyPair otherKeys;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        nimbus.setJwtValidator(JwtDecoderConfig.tokenValidator(ISSUER, AUDIENCE));
        decoder = nimbus;
    }

    private static String token(KeyPair signer, String issuer, String audience, String subject, Instant expiry)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(expiry));
        if (audience != null) {
            claims.audience(audience);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    private static String valid() throws Exception {
        return token(keys, ISSUER, AUDIENCE, "auth0|abc", Instant.now().plusSeconds(300));
    }

    @Test
    void validTokenIsAcceptedAndSubjectIsAvailable() throws Exception {
        Jwt jwt = decoder.decode(valid());
        assertThat(jwt.getSubject()).isEqualTo("auth0|abc");
    }

    @Test
    void wrongSignatureIsRejected() throws Exception {
        String t = token(otherKeys, ISSUER, AUDIENCE, "auth0|abc", Instant.now().plusSeconds(300));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String t = token(keys, "https://evil.test/", AUDIENCE, "auth0|abc", Instant.now().plusSeconds(300));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void issuerWithoutTrailingSlashIsRejected() throws Exception {
        String t = token(keys, "https://example.test", AUDIENCE, "auth0|abc", Instant.now().plusSeconds(300));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void missingAudienceIsRejected() throws Exception {
        String t = token(keys, ISSUER, null, "auth0|abc", Instant.now().plusSeconds(300));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        String t = token(keys, ISSUER, "https://other.test", "auth0|abc", Instant.now().plusSeconds(300));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void tokenExpiredThirtySecondsAgoIsStillAccepted() throws Exception {
        String t = token(keys, ISSUER, AUDIENCE, "auth0|abc", Instant.now().minusSeconds(30));
        assertThat(decoder.decode(t).getSubject()).isEqualTo("auth0|abc");
    }

    @Test
    void tokenExpiredTwoMinutesAgoIsRejected() throws Exception {
        String t = token(keys, ISSUER, AUDIENCE, "auth0|abc", Instant.now().minusSeconds(120));
        assertThrows(BadJwtException.class, () -> decoder.decode(t));
    }

    @Test
    void emptySubjectIsRejectedWithMarker() throws Exception {
        String t = token(keys, ISSUER, AUDIENCE, "", Instant.now().plusSeconds(300));
        BadJwtException e = assertThrows(BadJwtException.class, () -> decoder.decode(t));
        assertThat(e.getMessage()).contains(SubjectValidator.IDENTITY_UNRESOLVABLE_MARKER);
    }

    @Test
    void jwtDecoderBeanIsLazyAndFailsFastWhenIssuerIsUnreachable() throws Exception {
        String issuer = "http://localhost:0/issuer-placeholder/";
        JwtDecoder lazy = assertDoesNotThrow(
                () -> new JwtDecoderConfig().jwtDecoder(issuer, "https://test-api.example.test"));
        assertThat(lazy).isNotNull();

        String t = token(keys, issuer, "https://test-api.example.test", "auth0|abc", Instant.now().plusSeconds(300));
        Throwable thrown = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(Throwable.class, () -> lazy.decode(t)));
        StringBuilder chain = new StringBuilder();
        for (Throwable c = thrown; c != null; c = c.getCause()) {
            chain.append("\n  ").append(c.getClass().getName()).append(": ").append(c.getMessage());
        }
        System.out.println("UNREACHABLE-ISSUER CHAIN:" + chain);
    }
}
