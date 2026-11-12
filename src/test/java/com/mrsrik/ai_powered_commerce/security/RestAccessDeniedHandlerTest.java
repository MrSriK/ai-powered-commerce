package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mrsrik.ai_powered_commerce.common.error.ErrorResponses;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Plain unit test for {@link RestAccessDeniedHandler}, no Spring context. */
class RestAccessDeniedHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RestAccessDeniedHandler handler =
            new RestAccessDeniedHandler(new ErrorResponses(CLOCK, mapper));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse handle(String message) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException(message));
        return response;
    }

    private void authenticateWith(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("u", "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    void unregisteredAuthorityGivesUserNotRegistered() throws Exception {
        authenticateWith("ROLE_UNREGISTERED");
        MockHttpServletResponse response = handle("denied");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("USER_NOT_REGISTERED");
    }

    @Test
    void customerGivesForbidden() throws Exception {
        authenticateWith("ROLE_CUSTOMER");
        MockHttpServletResponse response = handle("denied");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void adminDeniedGivesForbidden() throws Exception {
        authenticateWith("ROLE_ADMIN");
        MockHttpServletResponse response = handle("denied");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void noAuthenticationGivesForbidden() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletResponse response = handle("denied");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void bodyHasUniformShape() throws Exception {
        authenticateWith("ROLE_CUSTOMER");
        MockHttpServletResponse response = handle("denied");
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("timestamp").isString()).isTrue();
        assertThat(json.get("timestamp").asString()).isEqualTo("2025-01-02T03:04:05Z");
        assertThat(json.get("status").isNumber()).isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(json.get("message").asString()).isNotBlank();
        assertThat(json.has("fieldErrors")).isFalse();
    }

    @Test
    void neverEchoesExceptionMessage() throws Exception {
        authenticateWith("ROLE_CUSTOMER");
        assertThat(handle("secret-detail-xyz").getContentAsString()).doesNotContain("secret-detail-xyz");
        authenticateWith("ROLE_UNREGISTERED");
        assertThat(handle("secret-detail-xyz").getContentAsString()).doesNotContain("secret-detail-xyz");
    }
}
