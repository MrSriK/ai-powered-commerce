package com.mrsrik.ai_powered_commerce.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestTokens;

/**
 * End-to-end integration tests for Resource Owner Password Grant authentication endpoints (Task 41).
 * Uses WireMock to stub Auth0 database signup and token endpoints.
 */
class AuthIT extends AbstractWebIT {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.auth.domain", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    private long userCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
    }

    @Test
    @DisplayName("Signup happy path: creates Auth0 user and provisions local profile, returns 201 with UserProfileDto")
    void signupHappyPath() throws Exception {
        String email = "jane.doe@example.test";
        String id = "6639abcde123456789";
        String expectedSub = "auth0|" + id;

        wireMockServer.stubFor(WireMock.post(urlEqualTo("/dbconnections/signup"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"_id\":\"" + id + "\",\"email\":\"" + email + "\",\"email_verified\":false}")));

        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "jane.doe@example.test",
                                  "password": "Password123456!",
                                  "phoneNumber": "+15551234567",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName", is("Jane")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("jane.doe@example.test")))
                .andExpect(jsonPath("$.phoneNumber", is("+15551234567")))
                .andExpect(jsonPath("$.role", is("CUSTOMER")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.access_token").doesNotExist());

        assertThat(userCount()).isEqualTo(beforeCount + 1);
        String externalAuthId = jdbcTemplate.queryForObject(
                "SELECT external_auth_id FROM users WHERE email = ?", String.class, email);
        assertThat(externalAuthId).isEqualTo(expectedSub);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/dbconnections/signup"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(containing("\"email\":\"jane.doe@example.test\""))
                .withRequestBody(containing("\"connection\":\"Username-Password-Authentication\""))
                .withRequestBody(containing("\"name\":\"Jane Doe\"")));
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("Signup with explicit role: creates user with requested role (ADMIN) in DB")
    void signupWithExplicitRoleCreatesAdminUser() throws Exception {
        String email = "admin.user@example.test";
        String id = "7739abcde123456789";
        String expectedSub = "auth0|" + id;

        wireMockServer.stubFor(WireMock.post(urlEqualTo("/dbconnections/signup"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"_id\":\"" + id + "\",\"email\":\"" + email + "\",\"email_verified\":false}")));

        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Admin",
                                  "lastName": "User",
                                  "email": "admin.user@example.test",
                                  "password": "Password123456!",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName", is("Admin")))
                .andExpect(jsonPath("$.lastName", is("User")))
                .andExpect(jsonPath("$.email", is("admin.user@example.test")))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.access_token").doesNotExist());

        assertThat(userCount()).isEqualTo(beforeCount + 1);
        String roleInDb = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE email = ?", String.class, email);
        assertThat(roleInDb).isEqualTo("ADMIN");
        String externalAuthId = jdbcTemplate.queryForObject(
                "SELECT external_auth_id FROM users WHERE email = ?", String.class, email);
        assertThat(externalAuthId).isEqualTo(expectedSub);
    }

    @Test
    @DisplayName("Login happy path: forwards credentials to Auth0 and returns token")
    void loginHappyPath() throws Exception {
        String email = "existing.user@example.test";
        String validJwt = TestTokens.token().email(email).subject("auth0|existing-sub").build();

        wireMockServer.stubFor(WireMock.post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + validJwt + "\",\"token_type\":\"Bearer\",\"expires_in\":86400}")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "existing.user@example.test",
                                  "password": "CorrectPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", is(validJwt)))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.expires_in", is(86400)));

        wireMockServer.verify(postRequestedFor(urlEqualTo("/oauth/token"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(containing("\"username\":\"existing.user@example.test\""))
                .withRequestBody(containing("\"grant_type\":\"password\"")));
    }

    @Test
    @DisplayName("Signup duplicate email in Auth0: returns 409 ALREADY_REGISTERED and creates nothing in DB")
    void signupDuplicateEmailInAuth0Returns409() throws Exception {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/dbconnections/signup"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"BadRequestError\",\"code\":\"user_exists\",\"description\":\"The user already exists.\"}")));

        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Duplicate",
                                  "lastName": "User",
                                  "email": "dup@example.test",
                                  "password": "Password123456!",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ALREADY_REGISTERED")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(userCount()).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("Login bad credentials: returns 401 INVALID_CREDENTIALS without leaking Auth0 error description")
    void loginBadCredentialsReturns401() throws Exception {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Wrong email or password.\"}")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "someone@example.test",
                                  "password": "WrongPassword123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Auth0 500 error during signup: returns 503 AUTH_TEMPORARILY_UNAVAILABLE and writes nothing to DB")
    void auth0OutageReturns503() throws Exception {
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/dbconnections/signup"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Outage",
                                  "lastName": "Test",
                                  "email": "outage@example.test",
                                  "password": "Password123456!",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("AUTH_TEMPORARILY_UNAVAILABLE")))
                .andExpect(jsonPath("$.status", is(503)));

        assertThat(userCount()).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("Signup validation failure: returns 400 VALIDATION_FAILED and never calls Auth0")
    void signupValidationFailureReturns400WithoutCallingAuth0() throws Exception {
        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "",
                                  "lastName": "User",
                                  "email": "invalid-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors").isArray());

        assertThat(userCount()).isEqualTo(beforeCount);
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/dbconnections/signup")));
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    @DisplayName("Signup missing role returns 400 VALIDATION_FAILED with field error on role")
    void signupMissingRoleReturns400() throws Exception {
        long beforeCount = userCount();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "No",
                                  "lastName": "Role",
                                  "email": "norole@example.test",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'role')]").isNotEmpty());

        assertThat(userCount()).isEqualTo(beforeCount);
        wireMockServer.verify(0, postRequestedFor(urlEqualTo("/dbconnections/signup")));
    }
}
