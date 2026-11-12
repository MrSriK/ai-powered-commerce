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

    private static final String REGISTER = "/api/users/register";
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
     * CHAIN-LEVEL path matrix for protected routes. The cart, orders, AI and catalog-write controllers do not
     * exist yet, so these rows only assert what the security chain decides before routing (401 / 403
     * USER_NOT_REGISTERED). They will gain real handlers in later phases; extend this list with new routes
     * and never assert a registered caller's result on these rows. Paths copied from the design endpoints table.
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

    private static final Route REGISTER_ROUTE = r(HttpMethod.POST, REGISTER);

    /** Every non-public route, including /register. */
    static List<Route> allNonPublic() {
        return Stream.of(CHAIN_PROTECTED.stream(), REAL_REGISTERED_ONLY.stream(), Stream.of(REGISTER_ROUTE))
                .flatMap(s -> s).toList();
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

    private ResultActions register(Caller caller, String json) throws Exception {
        return perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(json), caller);
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
    void unregisteredCallerCanRegisterThenIsAdmittedWithTheSameToken() throws Exception {
        Caller caller = callers.unregistered();
        error(perform(get(ME), caller), 403, "USER_NOT_REGISTERED");
        assertThat(userCount()).isZero();

        String created = register(caller, validBody()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(userCount()).isEqualTo(1);

        String me = perform(get(ME), caller).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(me).isEqualTo(created);

        // Proof that the chain now admits the user (registered callers pass the chain). A stable route is used;
        // the not-yet-existing GET /api/cart is only asserted to be neither 401 nor 403 so it survives later phases.
        perform(get(TEST_PROTECTED), caller).andExpect(status().isOk());
        assertThat(call(r(HttpMethod.GET, "/api/cart"), caller.bearer()).getStatus()).isNotIn(401, 403);
    }

    // ---------------------------------------------------------------- 3. registered and disabled callers

    @Test
    void registeredCallerOnRegisterGets409AndRowCountIsUnchanged() throws Exception {
        Caller caller = callers.registeredCustomer();
        long before = userCount();
        error(register(caller, validBody()), 409, "ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void softDeletedUserGets401AccountDisabledEverywhereAndIsNotRestored() throws Exception {
        Caller caller = callers.disabledCustomer();
        long before = userCount();
        List<Route> routes = List.of(r(HttpMethod.GET, ME), REGISTER_ROUTE, r(HttpMethod.GET, "/api/cart"),
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

    // ---------------------------------------------------------------- 5. validation limits (13.3)

    private static String local(int length) {
        return "a".repeat(length);
    }

    /** Valid address of exactly 254 characters (local part 64, labels of at most 63). */
    private static String email254() {
        String email = local(64) + "@" + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(61);
        assertThat(email).hasSize(254);
        return email;
    }

    /** Valid address of exactly 255 characters (only the size limit is violated). */
    private static String email255() {
        String email = local(64) + "@" + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(62);
        assertThat(email).hasSize(255);
        return email;
    }

    static Stream<Arguments> validationCases() {
        String ok = "ada@example.test";
        return Stream.of(
                Arguments.of("firstName 255 ok", body("a".repeat(255), "L", ok, null), List.of()),
                Arguments.of("firstName 256 bad", body("a".repeat(256), "L", ok, null), List.of("firstName")),
                Arguments.of("lastName 255 ok", body("A", "l".repeat(255), ok, null), List.of()),
                Arguments.of("lastName 256 bad", body("A", "l".repeat(256), ok, null), List.of("lastName")),
                Arguments.of("email 254 ok", body("A", "L", email254(), null), List.of()),
                Arguments.of("email 255 bad", body("A", "L", email255(), null), List.of("email")),
                Arguments.of("phone 15 ok", body("A", "L", ok, "1".repeat(15)), List.of()),
                Arguments.of("phone 16 bad", body("A", "L", ok, "1".repeat(16)), List.of("phoneNumber")),
                Arguments.of("blank firstName", body("   ", "L", ok, null), List.of("firstName")),
                Arguments.of("blank lastName", body("A", "", ok, null), List.of("lastName")),
                Arguments.of("blank email", body("A", "L", " ", null), List.of("email")),
                Arguments.of("malformed email", body("A", "L", "not-an-email", null), List.of("email")),
                Arguments.of("many bad fields at once",
                        body(" ", "x".repeat(256), "not-an-email", "1".repeat(16)),
                        List.of("firstName", "lastName", "email", "phoneNumber")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationCases")
    void registrationValidationLimits(String name, String json, List<String> invalidFields) throws Exception {
        Caller caller = callers.unregistered();
        if (invalidFields.isEmpty()) {
            register(caller, json).andExpect(status().isCreated());
            assertThat(userCount()).isEqualTo(1);
            return;
        }
        String response = error(register(caller, json), 400, "VALIDATION_FAILED").andReturn().getResponse()
                .getContentAsString();
        for (String field : invalidFields) {
            assertThat(response).contains("\"field\":\"" + field + "\"");
        }
        assertThat(response.split("\"field\":", -1).length - 1).isEqualTo(invalidFields.size());
        assertThat(userCount()).isZero();
    }

    // ---------------------------------------------------------------- 6. role in the body

    @Test
    void roleAdminInBodyIsIgnored() throws Exception {
        Caller caller = callers.unregistered();
        register(caller, "{\"firstName\":\"Ada\",\"lastName\":\"L\",\"email\":\"a@example.test\",\"role\":\"ADMIN\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE external_auth_id = ?", String.class,
                caller.sub())).isEqualTo("CUSTOMER");
    }

    // ---------------------------------------------------------------- 7. email_verified matrix

    /** Arguments: token email claim, token email_verified, body email, expected status, expected emailVerified. */
    static Stream<Arguments> emailVerifiedCases() {
        return Stream.of(
                Arguments.of("ada@example.test", true, "ada@example.test", 201, true),
                Arguments.of("ADA@Example.TEST", true, "ada@example.test", 201, true),
                Arguments.of("ada@example.test", false, "ada@example.test", 201, false),
                Arguments.of("ada@example.test", null, "ada@example.test", 201, false),
                Arguments.of(null, null, "ada@example.test", 201, false),
                Arguments.of(null, true, "ada@example.test", 201, false),
                Arguments.of("other@example.test", true, "ada@example.test", 400, null));
    }

    @ParameterizedTest(name = "claim={0} verified={1} body={2} -> {3} emailVerified={4}")
    @MethodSource("emailVerifiedCases")
    void emailVerifiedMatrix(String claimEmail, Boolean claimVerified, String bodyEmail, int expectedStatus,
            Boolean expectedVerified) throws Exception {
        Caller caller = callers.unregistered(t -> {
            if (claimEmail != null) {
                t.email(claimEmail);
            }
            if (claimVerified != null) {
                t.emailVerified(claimVerified);
            }
        });
        ResultActions result = register(caller, body("Ada", "Lovelace", bodyEmail, null));
        if (expectedStatus == 201) {
            result.andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(expectedVerified));
            assertThat(userCount()).isEqualTo(1);
        } else {
            error(result, 400, "EMAIL_MISMATCH");
            assertThat(userCount()).isZero();
        }
    }

    // ---------------------------------------------------------------- 8. email uniqueness

    @Test
    void sameEmailInAnotherCaseAsRegisteredUserIs409() throws Exception {
        testData.user(b -> b.email("taken@example.test"));
        long before = userCount();
        error(register(callers.unregistered(), body("A", "B", "TAKEN@Example.test", null)), 409,
                "EMAIL_ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void emailOfSoftDeletedUserIs409() throws Exception {
        testData.user(b -> b.email("gone@example.test").deletedAt(java.time.Instant.now()));
        long before = userCount();
        error(register(callers.unregistered(), body("A", "B", "gone@example.test", null)), 409,
                "EMAIL_ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- 9. content type and malformed JSON

    @Test
    void wrongContentTypeIs415() throws Exception {
        Caller caller = callers.unregistered();
        error(perform(post(REGISTER).contentType(MediaType.TEXT_PLAIN).content(validBody()), caller), 415,
                "UNSUPPORTED_MEDIA_TYPE");
        assertThat(userCount()).isZero();
    }

    @Test
    void malformedJsonIs400() throws Exception {
        error(register(callers.unregistered(), "{\"firstName\":"), 400, "MALFORMED_REQUEST");
        assertThat(userCount()).isZero();
    }
}
