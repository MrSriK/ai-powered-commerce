package com.mrsrik.ai_powered_commerce.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Uniform error body. {@code fieldErrors} is omitted when null or empty. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(Instant timestamp, int status, String code, String message,
        List<FieldErrorDetail> fieldErrors) {

    /** Builds a response stamped from the given clock; {@code fieldErrors} may be null or empty. */
    public static ErrorResponse of(Clock clock, int status, ErrorCode code, String message,
            List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(clock.instant(), status, code.name(), message, fieldErrors);
    }
}
