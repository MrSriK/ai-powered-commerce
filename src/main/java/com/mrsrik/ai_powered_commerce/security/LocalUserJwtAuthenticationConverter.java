package com.mrsrik.ai_powered_commerce.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

/**
 * Turns a validated Jwt into an application authentication using one read-only
 * lookup by subject. Authorities come from the database role, never from token
 * claims. Outcomes: active user gives {@link AuthenticatedUser}; soft-deleted
 * user throws {@link AccountDisabledException}; no row gives
 * {@link UnregisteredIdentity} with ROLE_UNREGISTERED. It never writes.
 *
 * <p>
 * Deliberately NOT a scanned component: the security configuration (task 13.1,
 * {@code SecurityConfig}) registers it with an explicit {@code @Bean} inside a
 * {@code @Configuration} class, which web slices do not scan.
 */
public class LocalUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String ROLE_UNREGISTERED = "ROLE_UNREGISTERED";

    private final UserRepository userRepository;

    public LocalUserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String sub = jwt.getSubject();
        return userRepository.findByExternalAuthId(sub)
                .map(user -> authenticated(user, jwt))
                .orElseGet(() -> new AppAuthenticationToken(
                        new UnregisteredIdentity(sub, jwt),
                        jwt,
                        List.of(new SimpleGrantedAuthority(ROLE_UNREGISTERED))));
    }

    private AbstractAuthenticationToken authenticated(User user, Jwt jwt) {
        if (user.getDeletedAt() != null) {
            throw new AccountDisabledException();
        }
        return new AppAuthenticationToken(
                new AuthenticatedUser(user.getId(), user.getRole(), jwt),
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
