package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.support.TestTokens;
import com.mrsrik.securityfixture.SecurityTestController;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The PRODUCTION jwtDecoder bean against a JDK HttpServer stub that serves OIDC discovery
 * and a JWK Set built from the test public key (Requirements 5.4, 5.5, 5.6).
 */
@AutoConfigureMockMvc
@Import(SecurityTestController.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityProviderStubIT extends AbstractPostgresIT {

    private static final String AUDIENCE = "https://stub-api.example.test";
    private static final String PROTECTED = "/api/_securitytest/protected";
    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final String JWKS_PATH = "/.well-known/jwks.json";

    private static final HttpServer SERVER;
    private static final String ISSUER;
    private static final AtomicInteger DISCOVERY_CALLS = new AtomicInteger();
    private static final AtomicInteger JWKS_CALLS = new AtomicInteger();
    /** While true the discovery endpoint answers 500 (simulated blip). */
    private static final AtomicBoolean DISCOVERY_BROKEN = new AtomicBoolean(true);

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            ISSUER = "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/";
            String jwksUri = ISSUER.substring(0, ISSUER.length() - 1) + JWKS_PATH;
            String discovery = "{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"" + jwksUri
                    + "\",\"subject_types_supported\":[\"public\"],"
                    + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
            RSAKey key = new RSAKey.Builder(TestTokens.publicKey())
                    .keyID("stub-key").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
            String jwks = new JWKSet(key).toString();
            SERVER.createContext(DISCOVERY_PATH, ex -> {
                DISCOVERY_CALLS.incrementAndGet();
                if (DISCOVERY_BROKEN.get()) {
                    respond(ex, 500, "{}");
                } else {
                    respond(ex, 200, discovery);
                }
            });
            SERVER.createContext(JWKS_PATH, ex -> {
                JWKS_CALLS.incrementAndGet();
                respond(ex, 200, jwks);
            });
            SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("app.auth.domain", () -> ISSUER);
        registry.add("app.auth.audience", () -> AUDIENCE);
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    private static TestTokens.Builder builder() {
        return TestTokens.token().issuer(ISSUER).audience(AUDIENCE);
    }

    private ResultActions call(String token) throws Exception {
        return mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token)));
    }

    private String registeredSubject() {
        String sub = "auth0|stub-" + java.util.UUID.randomUUID();
        testData.user(b -> b.externalAuthId(sub));
        return sub;
    }

    @Test
    @Order(1)
    void discoveryBlipGives503ThenRecovers() throws Exception {
        String sub = registeredSubject();
        String token = builder().subject(sub).build();

        call(token).andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_UNAVAILABLE"));
        assertThat(DISCOVERY_CALLS.get()).isGreaterThanOrEqualTo(1);
        assertThat(JWKS_CALLS.get()).isZero();

        DISCOVERY_BROKEN.set(false);

        call(token).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void validTokenOfRegisteredCustomerGives200AndJwksIsCached() throws Exception {
        String sub = registeredSubject();
        int discoveryBefore = DISCOVERY_CALLS.get();
        int jwksBefore = JWKS_CALLS.get();
        for (int i = 0; i < 3; i++) {
            call(builder().subject(sub).build()).andExpect(status().isOk());
        }
        assertThat(DISCOVERY_CALLS.get()).isEqualTo(discoveryBefore);
        assertThat(JWKS_CALLS.get() - jwksBefore).isZero();
        assertThat(JWKS_CALLS.get()).isEqualTo(1);
    }

    @Test
    void tokenSignedWithOtherKeyGives401InvalidToken() throws Exception {
        call(TestTokens.signWithOtherKey(builder())).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tokenWithoutSubjectGives401IdentityUnresolvable() throws Exception {
        call(builder().noSubject().build()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_UNRESOLVABLE"));
    }

    @Test
    void expiredTokenGives401InvalidToken() throws Exception {
        call(builder().issuedAt(java.time.Instant.now().minusSeconds(7200))
                .expiresIn(Duration.ofHours(-1)).build())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void wrongAudienceGives401InvalidToken() throws Exception {
        call(builder().audience("https://other-api.example.test").build())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void wrongIssuerClaimGives401InvalidToken() throws Exception {
        call(builder().issuer("https://evil.example.test/").build())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
