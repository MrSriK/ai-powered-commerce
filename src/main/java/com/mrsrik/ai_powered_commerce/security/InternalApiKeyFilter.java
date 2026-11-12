package com.mrsrik.ai_powered_commerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates internal calls by the {@code X-Internal-Api-Key} header (Requirements 12.10, 12.11).
 *
 * <p>A correct key yields an authentication holding only {@code ROLE_INTERNAL}. A missing or wrong
 * key is sent straight to the entry point as an {@link InvalidApiKeyException} (a filter's own
 * exception would not be translated). The key is compared in constant time and is never logged.
 * This class is deliberately not a bean: it is created by {@code InternalSecurityConfig} so
 * Spring Boot does not register it as a servlet filter for every request.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    /** Request header carrying the key. */
    public static final String HEADER = "X-Internal-Api-Key";

    static final String AUTHORITY = "ROLE_INTERNAL";

    private final byte[] expectedKey;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextHolderStrategy contextStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    public InternalApiKeyFilter(String expectedKey, AuthenticationEntryPoint entryPoint) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null || !matches(presented)) {
            contextStrategy.clearContext();
            entryPoint.commence(request, response, new InvalidApiKeyException());
            return;
        }
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "internal", null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
        SecurityContext context = contextStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextStrategy.setContext(context);
        chain.doFilter(request, response);
    }

    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }
}
