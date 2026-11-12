package com.mrsrik.ai_powered_commerce.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Serializes with the JsonMapper that Spring Boot auto-configures. */
@JsonTest
class ErrorResponseJsonTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);

    @Autowired
    private JsonMapper mapper;

    @Test
    void serializesTimestampAsIsoStringAndTypedFields() {
        ErrorResponse response = ErrorResponse.of(CLOCK, 404, ErrorCode.PRODUCT_NOT_FOUND, "Product not found", null);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

        assertTrue(json.get("timestamp").isString());
        assertEquals("2025-01-02T03:04:05Z", json.get("timestamp").asString());
        assertTrue(json.get("status").isNumber());
        assertEquals(404, json.get("status").asInt());
        assertEquals("PRODUCT_NOT_FOUND", json.get("code").asString());
        assertEquals("Product not found", json.get("message").asString());
    }

    @Test
    void omitsFieldErrorsWhenNullOrEmpty() {
        for (List<FieldErrorDetail> none : java.util.Arrays.asList(null, List.<FieldErrorDetail>of())) {
            ErrorResponse response = ErrorResponse.of(CLOCK, 400, ErrorCode.INVALID_PAGINATION, "bad", none);
            JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
            assertFalse(json.has("fieldErrors"));
        }
    }

    @Test
    void includesEveryFieldError() {
        List<FieldErrorDetail> details = List.of(
                new FieldErrorDetail("name", "NotBlank", "must not be blank"),
                new FieldErrorDetail("price", "Positive", "must be positive"));
        ErrorResponse response = ErrorResponse.of(CLOCK, 400, ErrorCode.VALIDATION_FAILED, "Validation failed", details);

        JsonNode errors = mapper.readTree(mapper.writeValueAsString(response)).get("fieldErrors");

        assertEquals(2, errors.size());
        assertEquals("name", errors.get(0).get("field").asString());
        assertEquals("NotBlank", errors.get(0).get("constraint").asString());
        assertEquals("must not be blank", errors.get(0).get("message").asString());
        assertEquals("price", errors.get(1).get("field").asString());
    }
}
