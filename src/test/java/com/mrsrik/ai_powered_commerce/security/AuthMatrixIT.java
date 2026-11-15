package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;
import com.mrsrik.ai_powered_commerce.support.TestTokens;
import com.mrsrik.securityfixture.SecurityTestController;

/**
 * 401/403 matrix and registration web tests: real signed tokens through the real decoder,
 * converter and filter chain, real PostgreSQL. No mocked decoder or repository.
 */
@Import(SecurityTestController.class)
class AuthMatrixIT extends AbstractWebIT {

    private static final String ME = "/api/users/me";
    private static final String TEST_PROTECTED = "/api/_securitytest/protected";
    private static final String TEST_ADMIN = "/api/_securitytest/admin";
    private static final String ID = "00000000-0000-4000-8000-000000000001";

    /** An HTTP method and path. */
    record Route(HttpMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static Route r(HttpMethod method, String path) {
        return new Route(method, path);
    }

    /**
     * CHAIN-LEVEL path matrix for protected routes.
     */
    static final List<Route> CHAIN_PROTECTED = List.of(
            r(HttpMethod.GET, "/api/cart"),
            r(HttpMethod.POST, "/api/cart/items"),
            r(HttpMethod.PUT, "/api/cart/items/" + ID),
            r(HttpMethod.DELETE, "/api/cart/items/" + ID),
            r(HttpMethod.GET, "/api/orders"),
            r(HttpMethod.POST, "/api/orders"),
            r(HttpMethod.GET, "/api/orders/" + ID),
            r(HttpMethod.POST, "/api/orders/" + ID + "/cancel"),
            r(HttpMethod.PATCH, "/api/orders/" + ID + "/status"),
            r(HttpMethod.POST, "/api/ai/conversations"),
            r(HttpMethod.GET, "/api/ai/conversations"),
            r(HttpMethod.POST, "/api/ai/conversations/" + ID + "/messages"),
            r(HttpMethod.GET, "/api/ai/conversations/" + ID + "/messages"),
            r(HttpMethod.POST, "/api/products"),
            r(HttpMethod.PUT, "/api/products/" + ID),
            r(HttpMethod.DELETE, "/api/products/" + ID),
            r(HttpMethod.POST, "/api/categories"),
            r(HttpMethod.PUT, "/api/categories/" + ID),
            r(HttpMethod.DELETE, "/api/categories/" + ID));

    /** Routes that exist now and need a registered user (profile, plus the test-only protected route). */
    static final List<Route> REAL_REGISTERED_ONLY = List.of(
            r(HttpMethod.GET, ME),
            r(HttpMethod.GET, TEST_PROTECTED),
            r(HttpMethod.GET, TEST_ADMIN));

    /** Public GET routes: the chain lets an absent token through (the handler may not exist yet, so 404). */
    static final List<Route> PUBLIC_GET = List.of(
            r(HttpMethod.GET, "/api/products"),
            r(HttpMethod.GET, "/api/products/" + ID),
            r(HttpMethod.GET, "/api/categories"),
            r(HttpMethod.GET, "/v3/api-docs"),
            r(HttpMethod.GET, "/swagger-ui/index.html"),
            r(HttpMethod.GET, "/swagger-ui.html"));

    /** Public POST routes: authentication endpoints permit absent token. */
    static final List<Route> PUBLIC_POST = List.of(
            r(HttpMethod.POST, "/api/auth/signup"),
            r(HttpMethod.POST, "/api/auth/login"));

    /** Every non-public route. */
    static List<Route> allNonPublic() {
        return Stream.concat(CHAIN_PROTECTED.stream(), REAL_REGISTERED_ONLY.stream()).toList();
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletResponse call(Route route, String authorization) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(route.method(), route.path())
                .contentType(MediaType.APPLICATION_JSON).content(validBody());
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static String validBody() {
        return body("Ada", "Lovelace", "ada@example.test", null);
    }

    private static String body(String first, String last, String email, String phone) {
        return "{\"firstName\":\"" + first + "\",\"lastName\":\"" + last + "\",\"email\":\"" + email + "\""
                + (phone == null ? "" : ",\"phoneNumber\":\"" + phone + "\"") + "}";
    }

    private long userCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
    }

    private ResultActions error(ResultActions r, int status, String code) throws Exception {
        return r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** Asserts status, code, standard error body fields and (for 401) the WWW-Authenticate header. */
    private void assertError(MockHttpServletResponse response, Route route, int status, String code) throws Exception {
        String what = route + " -> " + response.getStatus() + " " + response.getContentAsString();
        assertThat(response.getStatus()).as(what).isEqualTo(status);
        String json = response.getContentAsString();
        assertThat(json).as(what).contains("\"code\":\"" + code + "\"").contains("\"status\":" + status)
                .contains("\"timestamp\":").contains("\"message\":");
        if (status == 401) {
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).as(what).isEqualTo("Bearer");
        }
    }

    // ---------------------------------------------------------------- 1. 401 matrix

    /** Authorization header value (null = absent) and expected error code. */
    record BadAuth(String name, String header, String code) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<BadAuth> badAuthorizations() {
        return Stream.of(
                new BadAuth("no Authorization header", null, "AUTHENTICATION_REQUIRED"),
                new BadAuth("Basic scheme", "Basic dXNlcjpwYXNz", "AUTHENTICATION_REQUIRED"),
                new BadAuth("empty bearer token", "Bearer ", "INVALID_TOKEN"),
                new BadAuth("garbage token", "Bearer not-a-jwt", "INVALID_TOKEN"),
                new BadAuth("expired token", TestTokens.bearer(TestTokens.token()
                        .issuedAt(java.time.Instant.now().minus(Duration.ofHours(3)))
                        .expiresIn(Duration.ofHours(-2)).build()), "INVALID_TOKEN"),
                new BadAuth("bad signature", TestTokens.bearer(TestTokens.signWithOtherKey(TestTokens.token())),
                        "INVALID_TOKEN"),
                new BadAuth("wrong audience", TestTokens.bearer(TestTokens.token().audience("https://other.example")
                        .build()), "INVALID_TOKEN"),
                new BadAuth("wrong issuer", TestTokens.bearer(TestTokens.token().issuer("https://evil.example/")
                        .build()), "INVALID_TOKEN"),
                new BadAuth("token without sub", TestTokens.bearer(TestTokens.token().noSubject().build()),
                        "IDENTITY_UNRESOLVABLE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badAuthorizations")
    void missingOrInvalidCredentialsGive401OnEveryNonPublicRoute(BadAuth auth) throws Exception {
        for (Route route : allNonPublic()) {
            assertError(call(route, auth.header()), route, 401, auth.code());
        }
        assertThat(userCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badAuthorizations")
    void invalidTokenOnPublicGetRouteIs401ButAbsentTokenPasses(BadAuth auth) throws Exception {
        for (Route route : PUBLIC_GET) {
            MockHttpServletResponse response = call(route, auth.header());
            if (auth.header() == null || auth.header().startsWith("Basic ")) {
                // The chain does not authenticate: not rejected (404 until the controller exists).
                assertThat(response.getStatus()).as(route + " without bearer").isNotIn(401, 403);
            } else {
                // A present bearer token is always validated, even on public routes.
                assertError(response, route, 401, auth.code());
            }
        }
    }

    @Test
    void publicPostRoutesWithoutTokenAreNot401Or403() throws Exception {
        for (Route route : PUBLIC_POST) {
            MockHttpServletResponse response = call(route, null);
            assertThat(response.getStatus()).as(route + " without bearer").isNotIn(401, 403);
        }
    }

    // ---------------------------------------------------------------- 2. unregistered caller

    @Test
    void unregisteredCallerGets403OnProtectedRoutesAndCreatesNothing() throws Exception {
        Caller caller = callers.unregistered();
        List<Route> routes = Stream.concat(CHAIN_PROTECTED.stream(), REAL_REGISTERED_ONLY.stream()).toList();
        for (Route route : routes) {
            MockHttpServletResponse response = call(route, caller.bearer());
            assertError(response, route, 403, "USER_NOT_REGISTERED");
        }
        assertThat(userCount()).isZero();
    }

    @Test
    void unregisteredCallerAdmittedAfterRegistrationWithSameToken() throws Exception {
        Caller caller = callers.unregistered();
        error(perform(get(ME), caller), 403, "USER_NOT_REGISTERED");
        assertThat(userCount()).isZero();

        testData.user(b -> b.externalAuthId(caller.sub()));
        assertThat(userCount()).isEqualTo(1);

        String me = perform(get(ME), caller).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(me).isNotEmpty();

        perform(get(TEST_PROTECTED), caller).andExpect(status().isOk());
        assertThat(call(r(HttpMethod.GET, "/api/cart"), caller.bearer()).getStatus()).isNotIn(401, 403);
    }

    // ---------------------------------------------------------------- 3. registered and disabled callers

    @Test
    void softDeletedUserGets401AccountDisabledEverywhereAndIsNotRestored() throws Exception {
        Caller caller = callers.disabledCustomer();
        long before = userCount();
        List<Route> routes = List.of(r(HttpMethod.GET, ME), r(HttpMethod.GET, "/api/cart"),
                r(HttpMethod.GET, "/api/products"));
        for (Route route : routes) {
            assertError(call(route, caller.bearer()), route, 401, "ACCOUNT_DISABLED");
        }
        assertThat(userCount()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, caller.userId())).isTrue();
    }

    // ---------------------------------------------------------------- 4. role from the database

    @Test
    void customerOnAdminRouteGets403ForbiddenAndAdminGets200() throws Exception {
        error(perform(get(TEST_ADMIN), callers.registeredCustomer()), 403, "FORBIDDEN");
        perform(get(TEST_ADMIN), callers.registeredAdmin()).andExpect(status().isOk());
    }

    @Test
    void roleComesFromTheDatabaseNotFromTheTokenClaim() throws Exception {
        Caller customer = callers.registeredCustomer(t -> t.claim("roles", List.of("ADMIN")));
        error(perform(get(TEST_ADMIN), customer), 403, "FORBIDDEN");
        Caller admin = callers.registeredAdmin(t -> t.claim("roles", List.of("CUSTOMER")));
        perform(get(TEST_ADMIN), admin).andExpect(status().isOk());
    }
}
