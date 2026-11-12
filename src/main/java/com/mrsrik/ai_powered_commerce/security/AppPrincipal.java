package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Principal placed in the Authentication by the user-chain converter. */
public sealed interface AppPrincipal permits AuthenticatedUser, UnregisteredIdentity {

    /** The validated token this principal was derived from. */
    Jwt jwt();
}
