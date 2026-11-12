package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by the authentication converter when the token's subject belongs to a
 * soft-deleted user. It is an {@link AuthenticationException} (not an ApiException)
 * because it is raised before MVC, so the authentication entry point renders it as
 * 401 ACCOUNT_DISABLED. The message is fixed and carries no user data.
 */
public class AccountDisabledException extends AuthenticationException {

    private static final String MESSAGE = "Account is disabled";

    public AccountDisabledException() {
        super(MESSAGE);
    }
}
