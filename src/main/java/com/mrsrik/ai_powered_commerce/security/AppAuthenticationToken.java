package com.mrsrik.ai_powered_commerce.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authenticated token whose principal is an {@link AppPrincipal}, so that
 * {@link CurrentUser} can resolve the caller. Credentials are the validated Jwt;
 * the name is the token subject.
 */
public final class AppAuthenticationToken extends AbstractAuthenticationToken {

    private final AppPrincipal principal;
    private final Jwt jwt;

    public AppAuthenticationToken(AppPrincipal principal, Jwt jwt,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return jwt.getSubject();
    }
}
