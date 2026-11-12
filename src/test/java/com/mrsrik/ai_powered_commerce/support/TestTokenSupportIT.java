package com.mrsrik.ai_powered_commerce.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import com.mrsrik.securityfixture.SecurityTestController;

/** Proves the test token helpers drive the real decoder and converter end to end. */
@Import(SecurityTestController.class)
class TestTokenSupportIT extends AbstractWebIT {

    private static final String PROTECTED = "/api/_securitytest/protected";
    private static final String ADMIN = "/api/_securitytest/admin";

    private long userCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
    }

    private void expectBearer(String token, int status, String code) throws Exception {
        mockMvc.perform(get(PROTECTED).header("Authorization", TestTokens.bearer(token)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void defaultsMatchTestProfile() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application-test.properties")) {
            props.load(in);
        }
        assertThat(props.getProperty("app.auth.issuer-uri")).isEqualTo(TestTokens.ISSUER);
        assertThat(props.getProperty("app.auth.audience")).isEqualTo(TestTokens.AUDIENCE);
    }

    @Test
    void registeredCustomerIsAllowedAndTokenIsReusable() throws Exception {
        TestCallers.Caller c = callers.registeredCustomer(b -> b.email("a@b.test").emailVerified(true));
        perform(get(PROTECTED), c).andExpect(status().isOk());
        perform(get(PROTECTED), c).andExpect(status().isOk());
    }

    @Test
    void adminReachesAdminRouteAndCustomerIsForbidden() throws Exception {
        perform(get(ADMIN), callers.registeredAdmin()).andExpect(status().isOk());
        perform(get(ADMIN), callers.registeredCustomer()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unregisteredIsRejectedWithoutCreatingUser() throws Exception {
        long before = userCount();
        perform(get(PROTECTED), callers.unregistered()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_NOT_REGISTERED"));
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void disabledIsUnauthorized() throws Exception {
        perform(get(PROTECTED), callers.disabledCustomer()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        expectBearer(TestTokens.token().expiresIn(Duration.ofHours(-1)).build(), 401, "INVALID_TOKEN");
        expectBearer(TestTokens.token().audience("https://other.example.test").build(), 401, "INVALID_TOKEN");
        expectBearer(TestTokens.token().issuer("https://other.example.test/").build(), 401, "INVALID_TOKEN");
        expectBearer(TestTokens.signWithOtherKey(TestTokens.token()), 401, "INVALID_TOKEN");
    }

    @Test
    void missingOrBlankSubjectIsUnresolvable() throws Exception {
        expectBearer(TestTokens.token().noSubject().build(), 401, "IDENTITY_UNRESOLVABLE");
        expectBearer(TestTokens.token().subject("  ").build(), 401, "IDENTITY_UNRESOLVABLE");
    }

    @Test
    void rolesComeFromDatabaseNotToken() throws Exception {
        TestCallers.Caller c = callers.registeredCustomer(
                b -> b.claim("roles", List.of("ADMIN")).claim("scope", "admin"));
        perform(get(ADMIN), c).andExpect(status().isForbidden());
        perform(get(PROTECTED), c).andExpect(status().isOk());
    }
}
