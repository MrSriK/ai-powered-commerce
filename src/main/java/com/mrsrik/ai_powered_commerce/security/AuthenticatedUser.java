package com.mrsrik.ai_powered_commerce.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.user.enums.Role;

/** A registered, active user. The role comes from the database, never the token. */
public record AuthenticatedUser(UUID userId, Role role, Jwt jwt) implements AppPrincipal {
}
