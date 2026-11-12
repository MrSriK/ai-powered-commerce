package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.user.enums.Role;

class CurrentUserTest {

    private final Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("auth0|abc")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(AppPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(new AppAuthenticationToken(principal, jwt, List.of()));
    }

    @Test
    void idReturnsUserIdOfAuthenticatedUser() {
        UUID id = UUID.randomUUID();
        authenticate(new AuthenticatedUser(id, Role.CUSTOMER, jwt));

        assertThat(CurrentUser.id()).isEqualTo(id);
        assertThat(CurrentUser.jwt()).isSameAs(jwt);
        assertThat(CurrentUser.principal()).isInstanceOf(AuthenticatedUser.class);
    }

    @Test
    void idFailsForUnregisteredIdentityButJwtIsAvailable() {
        authenticate(new UnregisteredIdentity("auth0|abc", jwt));

        assertThatThrownBy(CurrentUser::id).isInstanceOf(IllegalStateException.class);
        assertThat(CurrentUser.jwt()).isSameAs(jwt);
    }

    @Test
    void failsWithoutAuthentication() {
        assertThatThrownBy(CurrentUser::id).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(CurrentUser::principal).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void sealedHierarchyPermitsExactlyTheTwoRecords() {
        Set<Class<?>> permitted = Stream.of(AppPrincipal.class.getPermittedSubclasses())
                .collect(Collectors.toSet());

        assertThat(AppPrincipal.class.isSealed()).isTrue();
        assertThat(permitted).containsExactlyInAnyOrder(AuthenticatedUser.class, UnregisteredIdentity.class);
    }
}
