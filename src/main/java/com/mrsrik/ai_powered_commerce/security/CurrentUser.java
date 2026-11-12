package com.mrsrik.ai_powered_commerce.security;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves the caller from the current SecurityContext. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * The current principal.
     *
     * @throws AuthenticationCredentialsNotFoundException if nobody is authenticated
     *                                                    or the principal is not an
     *                                                    {@link AppPrincipal}
     */
    public static AppPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal) {
            return principal;
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated application principal");
    }

    /**
     * The registered caller's id.
     *
     * @throws AuthenticationCredentialsNotFoundException if there is no principal
     * @throws IllegalStateException                      if the caller is an
     *                                                    {@link UnregisteredIdentity}
     */
    public static UUID id() {
        return switch (principal()) {
            case AuthenticatedUser user -> user.userId();
            case UnregisteredIdentity unregistered ->
                throw new IllegalStateException("Caller is not a registered user");
        };
    }

    /** The caller's token, for the registration controller. */
    public static Jwt jwt() {
        return principal().jwt();
    }
}
