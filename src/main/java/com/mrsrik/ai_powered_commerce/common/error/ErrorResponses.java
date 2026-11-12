package com.mrsrik.ai_powered_commerce.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Builds and writes the uniform error body, shared by the exception handler and the security handlers. */
@Component
public class ErrorResponses {

    private final Clock clock;
    private final JsonMapper mapper;

    public ErrorResponses(Clock clock, JsonMapper mapper) {
        this.clock = clock;
        this.mapper = mapper;
    }

    /** Builds a body stamped with the injected clock; {@code fieldErrors} may be null or empty. */
    public ErrorResponse build(int status, ErrorCode code, String message, List<FieldErrorDetail> fieldErrors) {
        return ErrorResponse.of(clock, status, code, message, fieldErrors);
    }

    /** Builds a body without field errors. */
    public ErrorResponse build(int status, ErrorCode code, String message) {
        return build(status, code, message, null);
    }

    /** Writes the body as UTF-8 JSON with the given status, for errors raised outside Spring MVC. */
    public void write(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        byte[] json = mapper.writeValueAsBytes(build(status, code, message));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(json);
    }
}
