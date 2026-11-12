package com.mrsrik.ai_powered_commerce.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

/** Registration and profile endpoints with real tokens, real security chain and real database. */
class UserControllerIT extends AbstractWebIT {

    private static final String REGISTER = "/api/users/register";
    private static final String ME = "/api/users/me";
    private static final String VALID = body("Ada", "Lovelace", "ada@example.test", null);

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
        return r.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void unregisteredCallerRegistersThenReadsOwnProfileWithSameToken() throws Exception {
        Caller caller = callers.unregistered();

        String response = register(caller, body(" Ada ", "Lovelace", "Ada@Example.TEST", "+15551234567"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.phoneNumber").value("+15551234567"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.externalAuthId").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbcTemplate.queryForObject("SELECT external_auth_id FROM users WHERE email = ?",
                String.class, "ada@example.test")).isEqualTo(caller.sub());

        String me = perform(get(ME), caller)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(me).isEqualTo(response);
    }

    @Test
    void roleInBodyIsIgnoredAndUserIsCustomer() throws Exception {
        Caller caller = callers.unregistered();
        register(caller, "{\"firstName\":\"Ada\",\"lastName\":\"L\",\"email\":\"a@example.test\",\"role\":\"ADMIN\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE external_auth_id = ?",
                String.class, caller.sub())).isEqualTo("CUSTOMER");
    }

    @Test
    void adminProfileShowsAdminRole() throws Exception {
        Caller admin = callers.registeredAdmin();
        perform(get(ME), admin).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void noTokenIs401() throws Exception {
        error(mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID)), 401,
                "AUTHENTICATION_REQUIRED");
        error(mockMvc.perform(get(ME)), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void invalidTokenIs401() throws Exception {
        MockHttpServletRequestBuilder request = post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                .content(VALID).header(HttpHeaders.AUTHORIZATION, "Bearer garbage");
        error(mockMvc.perform(request), 401, "INVALID_TOKEN");
        assertThat(userCount()).isZero();
    }

    @Test
    void registeredCallerGets409AlreadyRegistered() throws Exception {
        Caller caller = callers.registeredCustomer();
        long before = userCount();
        error(register(caller, VALID), 409, "ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void meForUnregisteredCallerIs403AndCreatesNothing() throws Exception {
        Caller caller = callers.unregistered();
        error(perform(get(ME), caller), 403, "USER_NOT_REGISTERED");
        assertThat(userCount()).isZero();
    }

    @Test
    void softDeletedUserIs401AccountDisabledOnBothRoutes() throws Exception {
        Caller caller = callers.disabledCustomer();
        error(perform(get(ME), caller), 401, "ACCOUNT_DISABLED");
        error(register(caller, VALID), 401, "ACCOUNT_DISABLED");
    }

    @Test
    void everyInvalidFieldIsListed() throws Exception {
        Caller caller = callers.unregistered();
        String json = body(" ", "x".repeat(256), "not-an-email", "1".repeat(16));
        String response = error(register(caller, json), 400, "VALIDATION_FAILED").andReturn().getResponse()
                .getContentAsString();
        for (String field : List.of("firstName", "lastName", "email", "phoneNumber")) {
            assertThat(response).contains("\"field\":\"" + field + "\"");
        }
        assertThat(userCount()).isZero();
    }

    @Test
    void overlongEmailIs400() throws Exception {
        Caller caller = callers.unregistered();
        String email = "a".repeat(242) + "@example.test";
        assertThat(email).hasSize(255);
        error(register(caller, body("A", "B", email, null)), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").isNotEmpty());
        assertThat(userCount()).isZero();
    }

    @Test
    void existingEmailInAnotherCaseIs409() throws Exception {
        testData.user(b -> b.email("taken@example.test"));
        long before = userCount();
        error(register(callers.unregistered(), body("A", "B", "TAKEN@Example.test", null)), 409,
                "EMAIL_ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void softDeletedUsersEmailIs409() throws Exception {
        testData.user(b -> b.email("gone@example.test").deletedAt(java.time.Instant.now()));
        long before = userCount();
        error(register(callers.unregistered(), body("A", "B", "gone@example.test", null)), 409,
                "EMAIL_ALREADY_REGISTERED");
        assertThat(userCount()).isEqualTo(before);
    }

    @Test
    void tokenEmailDifferentFromBodyIs400EmailMismatch() throws Exception {
        Caller caller = callers.unregistered(t -> t.email("other@example.test").emailVerified(true));
        error(register(caller, VALID), 400, "EMAIL_MISMATCH");
        assertThat(userCount()).isZero();
    }

    @Test
    void tokenEmailEqualIgnoringCaseWithVerifiedTrueGivesEmailVerified() throws Exception {
        Caller caller = callers.unregistered(t -> t.email("ADA@example.test").emailVerified(true));
        register(caller, VALID).andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void emailVerifiedFalseOrAbsentGivesFalse() throws Exception {
        Caller falseClaim = callers.unregistered(t -> t.email("ada@example.test").emailVerified(false));
        register(falseClaim, VALID).andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(false));

        Caller absent = callers.unregistered(t -> t.email("bob@example.test"));
        register(absent, body("Bob", "B", "bob@example.test", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        error(perform(post(REGISTER).contentType(MediaType.TEXT_PLAIN).content(VALID), callers.unregistered()), 415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void malformedJsonIs400() throws Exception {
        error(register(callers.unregistered(), "{\"firstName\":"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void registeringNeverCreatesAnAdmin() throws Exception {
        register(callers.unregistered(), VALID).andExpect(status().isCreated());
        List<Map<String, Object>> admins = jdbcTemplate.queryForList("SELECT id FROM users WHERE role = 'ADMIN'");
        assertThat(admins).isEmpty();
        assertThat(Role.valueOf(jdbcTemplate.queryForObject("SELECT role FROM users", String.class)))
                .isEqualTo(Role.CUSTOMER);
    }
}
