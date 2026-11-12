package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import jakarta.servlet.Filter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * End-to-end check of the internal chain (Requirements 12.10 to 12.12) on the real context.
 * Uses the Testcontainers PostgreSQL base class because the full context needs a DataSource.
 */
@AutoConfigureMockMvc
@Import(InternalChainIT.PingController.class)
class InternalChainIT extends AbstractPostgresIT {

    private static final String KEY = "test-internal-api-key";
    private static final String HEADER = "X-Internal-Api-Key";

    /**
     * Test-only routes. Nested in the test class, so Spring Boot's test type filter keeps component
     * scanning from registering it in other test contexts; it is added here with {@code @Import}.
     */
    @Controller
    @ResponseBody
    static class PingController {
        @PostMapping("/internal/_ping")
        String internalPing() {
            return "ok";
        }

        @GetMapping("/t-outside/ping")
        String outsidePing() {
            return "outside";
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void noKeyIs401InvalidApiKey() throws Exception {
        mvc.perform(post("/internal/_ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
    }

    @Test
    void wrongKeyIs401InvalidApiKey() throws Exception {
        MvcResult result = mvc.perform(post("/internal/_ping").header(HEADER, "nope-nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(KEY).doesNotContain("nope-nope");
    }

    @Test
    void rightKeyIs200() throws Exception {
        mvc.perform(post("/internal/_ping").header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void bearerJwtWithoutKeyIsNotAccepted() throws Exception {
        mvc.perform(post("/internal/_ping").header("Authorization", "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
    }

    @Test
    void rightKeyIgnoresGarbageBearerToken() throws Exception {
        mvc.perform(post("/internal/_ping").header(HEADER, KEY).header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void apiKeyHeaderIsIgnoredOutsideInternalPaths() throws Exception {
        MvcResult without = mvc.perform(get("/t-outside/ping")).andReturn();
        MvcResult withKey = mvc.perform(get("/t-outside/ping").header(HEADER, KEY)).andReturn();
        MvcResult withWrongKey = mvc.perform(get("/t-outside/ping").header(HEADER, "nope")).andReturn();

        // The key must neither grant access nor trigger INVALID_API_KEY on a user route.
        assertThat(withKey.getResponse().getStatus()).isEqualTo(without.getResponse().getStatus());
        assertThat(withWrongKey.getResponse().getStatus()).isEqualTo(without.getResponse().getStatus());
        assertThat(withKey.getResponse().getContentAsString()).doesNotContain("INVALID_API_KEY");
        assertThat(withWrongKey.getResponse().getContentAsString()).doesNotContain("INVALID_API_KEY");
        assertThat(withKey.getResponse().getContentAsString()).isNotEqualTo("outside");
    }

    @Test
    void keyFilterIsNotRegisteredAsAGlobalServletFilter() {
        assertThat(context.getBeansOfType(InternalApiKeyFilter.class)).isEmpty();
        Map<String, Filter> filters = context.getBeansOfType(Filter.class);
        assertThat(filters.values()).noneMatch(f -> f instanceof InternalApiKeyFilter);
        Map<String, FilterRegistrationBean> registrations = context.getBeansOfType(FilterRegistrationBean.class);
        assertThat(registrations.values()).noneMatch(r -> r.getFilter() instanceof InternalApiKeyFilter);
    }
}
