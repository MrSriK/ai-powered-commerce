package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.support.TestTokens;
import com.mrsrik.securityfixture.SecurityTestController;

/**
 * The PRODUCTION jwtDecoder bean (lazy, translating) pointed at an issuer on a
 * closed local
 * port. Requirement 5.6: unreachable identity provider gives 503
 * AUTH_TEMPORARILY_UNAVAILABLE.
 */
@AutoConfigureMockMvc
@Import(SecurityTestController.class)
class IdentityProviderOutageIT extends AbstractPostgresIT {

    private static final String ISSUER = "http://127.0.0.1:" + closedPort() + "/";
    private static final String AUDIENCE = "https://outage-api.example.test";
    private static final String PROTECTED = "/api/_securitytest/protected";

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("app.auth.issuer-uri", () -> ISSUER);
        registry.add("app.auth.audience", () -> AUDIENCE);
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Value("${app.auth.issuer-uri}")
    private String configuredIssuer;

    private static String token() {
        return TestTokens.bearer(TestTokens.token().issuer(ISSUER).audience(AUDIENCE).build());
    }

    private void assertOutage(ResultActions result) throws Exception {
        MvcResult done = result
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();
        String body = done.getResponse().getContentAsString();
        assertThat(body).doesNotContain("127.0.0.1", "localhost", "Exception", "Connect",
                "com.mrsrik", "java.", "org.springframework", "http://", "\tat ");
        assertThat(done.getResponse().getErrorMessage()).isNull();
        java.time.Instant.parse(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("timestamp").asText());
    }

    @Test
    void contextStartedWhileIssuerIsDown() {
        assertThat(configuredIssuer).isEqualTo(ISSUER);
        assertThat(context.getBean(JwtDecoder.class)).isNotNull();
    }

    @Test
    void protectedRouteWithWellFormedTokenGives503() throws Exception {
        assertOutage(mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, token())));
    }

    @Test
    void publicRouteCarryingTokenGives503() throws Exception {
        assertOutage(mvc.perform(get("/api/products/_ping").header(HttpHeaders.AUTHORIZATION, token())));
    }

    @Test
    void registerRouteGives503() throws Exception {
        assertOutage(mvc.perform(post("/api/users/register")
                .header(HttpHeaders.AUTHORIZATION, token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"phoneNumber\":\"+15550000000\"}")));
    }

    @Test
    void requestWithoutTokenIsStill401AuthenticationRequired() throws Exception {
        mvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void appKeepsWorkingAfterFailure() throws Exception {
        assertOutage(mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, token())));
        assertOutage(mvc.perform(get(PROTECTED).header(HttpHeaders.AUTHORIZATION, token())));
        mvc.perform(get("/api/products/_ping")).andExpect(status().isOk());
    }
}
