package com.mrsrik.ai_powered_commerce.security;

import com.mrsrik.ai_powered_commerce.common.error.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Renders every authentication failure as the uniform JSON error body. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ErrorResponses errorResponses;

    public RestAuthenticationEntryPoint(ErrorResponses errorResponses) {
        this.errorResponses = errorResponses;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        AuthFailureClassifier.Failure failure = AuthFailureClassifier.classify(authException);
        if (failure.status() == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "30");
            log.warn("Identity provider unreachable; responding {}", failure.code());
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (log.isDebugEnabled()) {
            log.debug("Authentication failure classified as {} {} ({})", failure.status(), failure.code(),
                    chainNames(authException));
        }
        errorResponses.write(response, failure.status(), failure.code(), failure.message());
    }

    private static String chainNames(Throwable failure) {
        StringBuilder names = new StringBuilder();
        int depth = 0;
        for (Throwable t = failure; t != null && depth < 10; t = t.getCause(), depth++) {
            if (depth > 0) {
                names.append(" <- ");
            }
            names.append(t.getClass().getSimpleName());
        }
        return names.toString();
    }
}
