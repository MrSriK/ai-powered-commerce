package com.mrsrik.ai_powered_commerce.security;

import com.mrsrik.ai_powered_commerce.common.error.ErrorCode;
import com.mrsrik.ai_powered_commerce.common.error.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes a 403 error body for authenticated callers who are not allowed. The code is
 * USER_NOT_REGISTERED when the caller holds ROLE_UNREGISTERED, otherwise FORBIDDEN.
 * Messages are fixed and never echo the exception message.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    static final String UNREGISTERED_AUTHORITY = "ROLE_UNREGISTERED";

    private final ErrorResponses errorResponses;

    public RestAccessDeniedHandler(ErrorResponses errorResponses) {
        this.errorResponses = errorResponses;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        if (isUnregistered(SecurityContextHolder.getContext().getAuthentication())) {
            errorResponses.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.USER_NOT_REGISTERED,
                    "Registration is required before using this endpoint");
        } else {
            errorResponses.write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You do not have permission to perform this action");
        }
    }

    private static boolean isUnregistered(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof UnregisteredIdentity) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> UNREGISTERED_AUTHORITY.equals(a.getAuthority()));
    }
}
