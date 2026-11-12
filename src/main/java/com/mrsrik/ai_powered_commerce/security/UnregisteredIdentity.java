package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** A valid token whose subject has no local user yet. Carries sub and token claims only. */
public record UnregisteredIdentity(String sub, Jwt jwt) implements AppPrincipal {
}
