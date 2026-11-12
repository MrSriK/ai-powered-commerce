package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

class LocalUserJwtAuthenticationConverterTest {

    private static final String SUB = "auth0|abc";

    private final UserRepository repository = mock(UserRepository.class);
    private final LocalUserJwtAuthenticationConverter converter =
            new LocalUserJwtAuthenticationConverter(repository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
    }

    private static User user(Role role, Instant deletedAt) {
        return User.builder().id(UUID.randomUUID()).externalAuthId(SUB).role(role).deletedAt(deletedAt).build();
    }

    private static List<String> authorities(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void activeCustomerGetsOnlyRoleCustomer() {
        User user = user(Role.CUSTOMER, null);
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user));
        Jwt jwt = jwtBuilder().build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorities(token)).containsExactly("ROLE_CUSTOMER");
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getName()).isEqualTo(SUB);
        assertThat(token.getPrincipal()).isEqualTo(new AuthenticatedUser(user.getId(), Role.CUSTOMER, jwt));
    }

    @Test
    void activeAdminGetsOnlyRoleAdmin() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user(Role.ADMIN, null)));

        assertThat(authorities(converter.convert(jwtBuilder().build()))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void tokenClaimsNeverGrantAuthorities() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user(Role.CUSTOMER, null)));
        Jwt claimsJwt = jwtBuilder()
                .claim("roles", List.of("ADMIN"))
                .claim("scope", "admin")
                .claim("permissions", List.of("admin:all"))
                .claim("email", "admin@example.com")
                .build();

        assertThat(authorities(converter.convert(claimsJwt))).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void adminRoleComesFromDatabaseEvenWithoutClaims() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user(Role.ADMIN, null)));

        assertThat(authorities(converter.convert(jwtBuilder().build()))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void softDeletedUserIsRejectedAsDisabled() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user(Role.ADMIN, Instant.now())));

        assertThatThrownBy(() -> converter.convert(jwtBuilder().build()))
                .isInstanceOf(AccountDisabledException.class)
                .hasMessage("Account is disabled");
        verify(repository).findByExternalAuthId(SUB);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void unknownSubjectIsUnregisteredWithSingleAuthority() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.empty());
        Jwt jwt = jwtBuilder().claim("roles", List.of("ADMIN")).build();

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getPrincipal()).isEqualTo(new UnregisteredIdentity(SUB, jwt));
        assertThat(authorities(token)).containsExactly("ROLE_UNREGISTERED");
    }

    @Test
    void repositoryIsReadExactlyOnceAndNeverWritten() {
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user(Role.CUSTOMER, null)));

        converter.convert(jwtBuilder().build());

        verify(repository).findByExternalAuthId(SUB);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void currentUserWorksOnProducedTokens() {
        User user = user(Role.CUSTOMER, null);
        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwtBuilder().build()));
        assertThat(CurrentUser.id()).isEqualTo(user.getId());

        when(repository.findByExternalAuthId(SUB)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(converter.convert(jwtBuilder().build()));
        assertThatThrownBy(CurrentUser::id).isInstanceOf(IllegalStateException.class);
        assertThat(CurrentUser.jwt().getSubject()).isEqualTo(SUB);
    }
}
