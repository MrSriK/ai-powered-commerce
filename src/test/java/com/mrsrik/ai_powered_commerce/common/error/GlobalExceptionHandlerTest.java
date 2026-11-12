package com.mrsrik.ai_powered_commerce.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Web slice with Boot's real converters and Jackson config, so the JSON shape is the real one. */
@WebMvcTest(controllers = TestThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, ErrorResponses.class, GlobalExceptionHandlerTest.FixedClockConfig.class })
class GlobalExceptionHandlerTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    /** Asserts the common envelope: ISO timestamp string, numeric status, code, message. */
    private static ResultActions expectError(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.timestamp").value("2025-01-02T03:04:05Z"))
                .andExpect(jsonPath("$.status").isNumber())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    static Stream<Arguments> apiExceptions() {
        return Stream.of(
                Arguments.of("notfound", 404, "PRODUCT_NOT_FOUND"),
                Arguments.of("duplicate", 409, "DUPLICATE_SKU"),
                Arguments.of("stock", 409, "INSUFFICIENT_STOCK"),
                Arguments.of("ai", 502, "AI_PROVIDER_UNAVAILABLE"),
                Arguments.of("pagination", 400, "INVALID_PAGINATION"));
    }

    @ParameterizedTest
    @MethodSource("apiExceptions")
    void apiExceptionKeepsItsOwnStatusAndCode(String kind, int status, String code) throws Exception {
        expectError(mvc.perform(get("/t/api/" + kind)), status, code)
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void bodyValidationListsEveryInvalidFieldSorted() throws Exception {
        String json = "{\"name\":\"\",\"price\":-5,\"sku\":\"a\"}";

        MvcResult result = expectError(
                mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content(json)),
                400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].constraint").value("NotBlank"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[1].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[1].constraint").value("Positive"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("sku"))
                .andExpect(jsonPath("$.fieldErrors[2].constraint").value("Size"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Payload");
    }

    @Test
    void parameterValidationListsEveryInvalidParameterSorted() throws Exception {
        expectError(mvc.perform(get("/t/size?size=0&page=-1")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].constraint").value("Min"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("size"));
    }

    @Test
    void constraintViolationExceptionListsEveryViolationSorted() throws Exception {
        expectError(mvc.perform(get("/t/constraint")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].constraint").value("NotBlank"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("sku"));
    }

    @Test
    void malformedUuidInPathIsInvalidIdentifier() throws Exception {
        MvcResult result = expectError(mvc.perform(get("/t/uuid/not-a-uuid")), 400, "INVALID_IDENTIFIER")
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("not-a-uuid").doesNotContain("java.").doesNotContain("UUID");
    }

    @Test
    void unknownEnumValueIsInvalidIdentifier() throws Exception {
        expectError(mvc.perform(get("/t/enum?color=PURPLE")), 400, "INVALID_IDENTIFIER");
    }

    @Test
    void missingRequiredParameterIsMalformedRequest() throws Exception {
        expectError(mvc.perform(get("/t/param")), 400, "MALFORMED_REQUEST");
    }

    @Test
    void invalidJsonBodyIsMalformedRequestWithoutParserDetails() throws Exception {
        MvcResult result = expectError(
                mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": ")),
                400, "MALFORMED_REQUEST")
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Unexpected").doesNotContain("tools.jackson");
    }

    @Test
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        expectError(mvc.perform(post("/t/body").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void wrongHttpMethodIsMethodNotAllowedWithAllowHeader() throws Exception {
        expectError(mvc.perform(post("/t/boom")), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void unknownRouteIsRouteNotFound() throws Exception {
        expectError(mvc.perform(get("/t/does-not-exist")), 404, "ROUTE_NOT_FOUND")
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void accessDeniedIsForbiddenNotInternalError() throws Exception {
        MvcResult result = expectError(mvc.perform(get("/t/denied")), 403, "FORBIDDEN").andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("method security says no");
    }

    @Test
    void unexpectedExceptionIsGenericInternalErrorWithoutLeaks() throws Exception {
        MvcResult result = expectError(mvc.perform(get("/t/boom")), 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();
        assertNoLeaks(result.getResponse().getContentAsString());
    }

    @Test
    void dataIntegrityViolationFallsToTheCatchAll() throws Exception {
        MvcResult result = expectError(mvc.perform(get("/t/integrity")), 500, "INTERNAL_ERROR").andReturn();
        String body = result.getResponse().getContentAsString();
        assertNoLeaks(body);
        assertThat(body).doesNotContain("uq_products_sku");
    }

    private static void assertNoLeaks(String body) {
        List.of("jdbc:postgresql", "secret-host", "hunter2", "RuntimeException", "java.lang", "at com.",
                "DataIntegrityViolationException").forEach(leak -> assertThat(body).doesNotContain(leak));
    }
}
