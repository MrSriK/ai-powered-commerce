package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mrsrik.ai_powered_commerce.common.error.ErrorResponses;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class InternalApiKeyFilterTest {

    private static final String KEY = "correct-horse-battery-staple";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY,
            new RestAuthenticationEntryPoint(new ErrorResponses(CLOCK, MAPPER)));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/orders/advance");
        if (header != null) {
            request.addHeader(InternalApiKeyFilter.HEADER, header);
        }
        return request;
    }

    @Test
    void correctKeyContinuesWithExactlyRoleInternal() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, jakarta.servlet.ServletException {
                seen.set(SecurityContextHolder.getContext().getAuthentication());
                super.doFilter(req, res);
            }
        };

        filter.doFilter(request(KEY), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().isAuthenticated()).isTrue();
        assertThat(seen.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_INTERNAL");
    }

    @Test
    void missingHeaderGives401InvalidApiKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(null), response, chain);

        assertRejected(response, chain, null);
    }

    @ParameterizedTest
    @ValueSource(strings = { "wrong", "", " ", "   ", "correct-horse-battery-stapl", "correct-horse-battery-staple2",
            " correct-horse-battery-staple", "correct-horse-battery-staple ", "CORRECT-HORSE-BATTERY-STAPLE" })
    void wrongOrMalformedKeyGives401(String presented) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(presented), response, chain);

        assertRejected(response, chain, presented);
    }

    private void assertRejected(MockHttpServletResponse response, MockFilterChain chain, String presented)
            throws IOException {
        assertThat(chain.getRequest()).as("controller chain must not run").isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("code").asString()).isEqualTo("INVALID_API_KEY");
        assertThat(json.get("status").asInt()).isEqualTo(401);
        String everything = response.getContentAsString() + response.getHeaderNames().stream()
                .map(n -> n + "=" + response.getHeaders(n)).toList();
        assertThat(everything).doesNotContain(KEY);
        if (presented != null && presented.length() > 3) {
            assertThat(everything).doesNotContain(presented);
        }
    }
}
