package com.mrsrik.ai_powered_commerce.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signs real RS256 access tokens with a local RSA key for web tests.
 * The key pair is generated once per JVM. Defaults match the test profile
 * ({@code application-test.properties}); {@link TestTokenSupportIT} fails if they drift apart.
 */
public final class TestTokens {

    /** Must equal {@code app.auth.issuer-uri} in application-test.properties. */
    public static final String ISSUER = "http://localhost:0/issuer-placeholder/";
    /** Must equal {@code app.auth.audience} in application-test.properties. */
    public static final String AUDIENCE = "https://test-api.example.test";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private TestTokens() {
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Public key matching the signing key; used by the test decoder. */
    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    /** Value for the Authorization header. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Starts a builder with valid defaults. */
    public static Builder token() {
        return new Builder();
    }

    /** A token that is valid except that it is signed by a different key. */
    public static String signWithOtherKey(Builder builder) {
        return builder.sign(OTHER_KEYS);
    }

    /** Fluent token builder. */
    public static final class Builder {

        private String subject = "auth0|" + UUID.randomUUID();
        private boolean subjectSet = true;
        private String email;
        private Boolean emailVerified;
        private String issuer = ISSUER;
        private List<String> audience = List.of(AUDIENCE);
        private Instant issuedAt = Instant.now().minusSeconds(5);
        private Duration expiresIn = Duration.ofHours(1);
        private final Map<String, Object> claims = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder subject(String subject) {
            this.subject = subject;
            this.subjectSet = true;
            return this;
        }

        /** Omits the {@code sub} claim. */
        public Builder noSubject() {
            this.subjectSet = false;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder emailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        public Builder audience(String... audience) {
            this.audience = List.of(audience);
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        /** Lifetime counted from now; a negative duration gives an expired token. */
        public Builder expiresIn(Duration expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder claim(String name, Object value) {
            this.claims.put(name, value);
            return this;
        }

        /** The subject currently set (null if omitted). */
        public String subjectValue() {
            return subjectSet ? subject : null;
        }

        /** Signs with the shared test key and returns the compact serialization. */
        public String build() {
            return sign(KEYS);
        }

        private String sign(KeyPair signer) {
            JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(Instant.now().plus(expiresIn)));
            if (subjectSet) {
                b.subject(subject);
            }
            if (email != null) {
                b.claim("email", email);
            }
            if (emailVerified != null) {
                b.claim("email_verified", emailVerified);
            }
            claims.forEach(b::claim);
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), b.build());
                jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
                return jwt.serialize();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
