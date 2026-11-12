package com.mrsrik.ai_powered_commerce.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Always delegates to {@link RestAuthenticationEntryPoint}, so an
 * {@code AuthenticationServiceException} is never rethrown as a container 500.
 */
@Component
public class RestAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final RestAuthenticationEntryPoint entryPoint;

    public RestAuthenticationFailureHandler(RestAuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        entryPoint.commence(request, response, exception);
    }
}
