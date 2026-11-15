package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;
import com.mrsrik.ai_powered_commerce.user.service.UserWriter;
import com.mrsrik.securityfixture.SecurityTestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User filter chain end to end with a mocked JwtDecoder and UserRepository
 * (no Auth0, no user rows). Extends the Postgres base because the full
 * application context needs a DataSource and Flyway.
 */
@AutoConfigureMockMvc
@Import(SecurityTestController.class)
class SecurityConfigIT extends AbstractPostgresIT {

    private static final String SUB = "auth0|abc";
    private static final String PROTECTED = "/api/_securitytest/protected";
    private static final String ADMIN = "/api/_securitytest/admin";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private JwtDecoder decoder;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private UserWriter userWriter;

    @BeforeEach
    void stubDecoder() {
        when(decoder.decode("good")).thenReturn(jwt());
        when(decoder.decode("garbage")).thenThrow(new BadJwtException("bad signature"));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("good").header("alg", "RS256").subject(SUB)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private void registered(Role role, Instant deletedAt) {
        when(users.findByExternalAuthId(SUB)).thenReturn(Optional.of(
                User.builder().id(UUID.randomUUID()).externalAuthId(SUB).role(role).deletedAt(deletedAt).build()));
    }

    private void unregistered() {
        when(users.findByExternalAuthId(anyString())).thenReturn(Optional.empty());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static ResultActions code(ResultActions r, int status, String code) throws Exception {
        return r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void exactlyOneChainMatchesApiPaths() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/orders");
        long matching = context.getBeansOfType(SecurityFilterChain.class).values().stream()
                .filter(c -> c.matches(request)).count();
        assertThat(matching).isEqualTo(1);
    }

    @Test
    void noTokenOnProtectedRouteIs401AuthenticationRequired() throws Exception {
        code(mvc.perform(get(PROTECTED)), 401, "AUTHENTICATION_REQUIRED")
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void garbageTokenIs401InvalidToken() throws Exception {
        code(mvc.perform(get(PROTECTED).header("Authorization", bearer("garbage"))), 401, "INVALID_TOKEN")
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void identityProviderFailureIs503NotContainer500() throws Exception {
        when(decoder.decode("idp-down")).thenThrow(
                new JwtException("jwks", new ResourceAccessException("connect refused")));
        code(mvc.perform(get(PROTECTED).header("Authorization", bearer("idp-down"))), 503,
                "AUTH_TEMPORARILY_UNAVAILABLE")
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void authenticationServiceExceptionFromDecoderIsRenderedNot500() throws Exception {
        when(decoder.decode("svc")).thenThrow(new AuthenticationServiceException("x",
                new JwtException("jwks", new ResourceAccessException("down"))));
        code(mvc.perform(get(PROTECTED).header("Authorization", bearer("svc"))), 503,
                "AUTH_TEMPORARILY_UNAVAILABLE");
    }

    @Test
    void registeredCustomerReachesProtectedRoute() throws Exception {
        registered(Role.CUSTOMER, null);
        mvc.perform(get(PROTECTED).header("Authorization", bearer("good")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void unregisteredIdentityOnProtectedRouteIs403UserNotRegistered() throws Exception {
        unregistered();
        code(mvc.perform(get(PROTECTED).header("Authorization", bearer("good"))), 403, "USER_NOT_REGISTERED");
    }

    @Test
    void authRoutesArePermitAllWithoutToken() throws Exception {
        mvc.perform(post("/api/auth/login"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        mvc.perform(post("/api/auth/signup"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void softDeletedUserIs401AccountDisabled() throws Exception {
        registered(Role.CUSTOMER, Instant.now());
        code(mvc.perform(get(PROTECTED).header("Authorization", bearer("good"))), 401, "ACCOUNT_DISABLED")
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void customerOnAdminMethodIs403Forbidden() throws Exception {
        registered(Role.CUSTOMER, null);
        code(mvc.perform(get(ADMIN).header("Authorization", bearer("good"))), 403, "FORBIDDEN")
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void adminOnAdminMethodIs200() throws Exception {
        registered(Role.ADMIN, null);
        mvc.perform(get(ADMIN).header("Authorization", bearer("good"))).andExpect(status().isOk());
    }

    @Test
    void publicRouteWithoutTokenIs200() throws Exception {
        mvc.perform(get("/api/products/_ping")).andExpect(status().isOk());
    }

    @Test
    void publicRouteWithInvalidTokenIs401() throws Exception {
        code(mvc.perform(get("/api/products/_ping").header("Authorization", bearer("garbage"))), 401,
                "INVALID_TOKEN");
    }

    @Test
    void publicRouteWithSoftDeletedUserTokenIs401AccountDisabled() throws Exception {
        registered(Role.CUSTOMER, Instant.now());
        code(mvc.perform(get("/api/products/_ping").header("Authorization", bearer("good"))), 401,
                "ACCOUNT_DISABLED");
    }
}
