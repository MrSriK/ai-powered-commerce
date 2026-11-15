package com.mrsrik.ai_powered_commerce.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mrsrik.ai_powered_commerce.common.error.AlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.EmailAlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.EmailMismatchException;
import com.mrsrik.ai_powered_commerce.common.error.UserNotFoundException;
import com.mrsrik.ai_powered_commerce.user.dto.RegisterUserRequest;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

class UserRegistrationServiceTest {

    private static final String SUB = "auth0|abc";

    private UserRepository userRepository;
    private UserWriter userWriter;
    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userWriter = mock(UserWriter.class);
        service = new UserRegistrationService(userRepository, userWriter);
    }

    private static Jwt jwt(String sub, Object email, Object emailVerified) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(sub);
        if (email != null) {
            b.claim("email", email);
        }
        if (emailVerified != null) {
            b.claim("email_verified", emailVerified);
        }
        return b.build();
    }

    private static RegisterUserRequest request(String email) {
        return new RegisterUserRequest("Ada", "Lovelace", email, "+15550001111", Role.CUSTOMER);
    }

    private static User saved(String email, boolean verified) {
        return User.builder().id(UUID.randomUUID()).firstName("Ada").lastName("Lovelace").email(email)
                .phoneNumber("+15550001111").emailVerified(verified).role(Role.CUSTOMER)
                .createdAt(Instant.now()).build();
    }

    private static DataIntegrityViolationException violation(String constraint) {
        String raw = "SERROR\0C23505\0Mduplicate key\0" + (constraint == null ? "" : "n" + constraint + "\0") + "\0";
        return new DataIntegrityViolationException("dup", new PSQLException(new ServerErrorMessage(raw)));
    }

    private void stubInsert(boolean verified) {
        when(userWriter.insert(eq(SUB), eq("Ada"), eq("Lovelace"), eq("ada@example.test"), eq("+15550001111"),
                eq(verified), eq(Role.CUSTOMER))).thenReturn(saved("ada@example.test", verified));
    }

    private void assertNothingInserted() {
        verify(userWriter, never()).insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any());
    }

    @Test
    void registersCustomerWithNormalizedEmailAndReturnsProfile() {
        stubInsert(false);
        UserProfileDto dto = service.register(jwt(SUB, null, null), request("  Ada@Example.TEST "));
        assertThat(dto.email()).isEqualTo("ada@example.test");
        assertThat(dto.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registersUserWithSpecifiedRole() {
        when(userWriter.insert(eq(SUB), eq("Ada"), eq("Lovelace"), eq("ada@example.test"), eq("+15550001111"),
                eq(false), eq(Role.ADMIN))).thenReturn(
                        User.builder().id(UUID.randomUUID()).firstName("Ada").lastName("Lovelace").email("ada@example.test")
                                .phoneNumber("+15550001111").emailVerified(false).role(Role.ADMIN)
                                .createdAt(Instant.now()).build());
        RegisterUserRequest adminRequest = new RegisterUserRequest("Ada", "Lovelace", "ada@example.test", "+15550001111", Role.ADMIN);
        UserProfileDto dto = service.register(SUB, adminRequest, false);
        assertThat(dto.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void caseDifferenceAloneIsNotAMismatch() {
        stubInsert(true);
        UserProfileDto dto = service.register(jwt(SUB, "ADA@example.test", true), request("ada@Example.test"));
        assertThat(dto.emailVerified()).isTrue();
    }

    @Test
    void differentTokenEmailIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.register(jwt(SUB, "other@example.test", true), request("ada@example.test")))
                .isInstanceOf(EmailMismatchException.class);
        verify(userRepository, never()).existsByExternalAuthId(anyString());
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        assertNothingInserted();
    }

    @Test
    void subAlreadyRegisteredIsRejectedBeforeEmailCheck() {
        when(userRepository.existsByExternalAuthId(SUB)).thenReturn(true);
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isInstanceOf(AlreadyRegisteredException.class);
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        assertNothingInserted();
    }

    @Test
    void existingEmailIsRejectedUsingNormalizedValue() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ADA@Example.test")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertNothingInserted();
    }

    @Test
    void checksRunInDocumentedOrder() {
        stubInsert(false);
        service.register(jwt(SUB, null, null), request("ada@example.test"));
        InOrder order = inOrder(userRepository, userWriter);
        order.verify(userRepository).existsByExternalAuthId(SUB);
        order.verify(userRepository).existsByEmailIgnoreCase("ada@example.test");
        order.verify(userWriter).insert(eq(SUB), anyString(), anyString(), anyString(), any(), anyBoolean(), eq(Role.CUSTOMER));
    }

    @Test
    void emailVerifiedClaimTrueWithSameEmail() {
        stubInsert(true);
        assertThat(service.register(jwt(SUB, "ada@example.test", true), request("ada@example.test")).emailVerified())
                .isTrue();
    }

    @Test
    void emailVerifiedStringTrueIsAccepted() {
        stubInsert(true);
        assertThat(service.register(jwt(SUB, "ada@example.test", "true"), request("ada@example.test"))
                .emailVerified()).isTrue();
    }

    @Test
    void emailVerifiedClaimFalseGivesFalse() {
        stubInsert(false);
        assertThat(service.register(jwt(SUB, "ada@example.test", false), request("ada@example.test"))
                .emailVerified()).isFalse();
    }

    @Test
    void emailVerifiedClaimWithoutEmailClaimGivesFalse() {
        stubInsert(false);
        assertThat(service.register(jwt(SUB, null, true), request("ada@example.test")).emailVerified()).isFalse();
    }

    @Test
    void emailClaimWithoutEmailVerifiedClaimGivesFalse() {
        stubInsert(false);
        assertThat(service.register(jwt(SUB, "ada@example.test", null), request("ada@example.test"))
                .emailVerified()).isFalse();
    }

    @Test
    void unknownStringClaimGivesFalse() {
        stubInsert(false);
        assertThat(service.register(jwt(SUB, "ada@example.test", "yes"), request("ada@example.test"))
                .emailVerified()).isFalse();
    }

    @Test
    void externalAuthIdViolationMapsToAlreadyRegistered() {
        when(userWriter.insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenThrow(violation("users_external_auth_id_key"));
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isInstanceOf(AlreadyRegisteredException.class);
    }

    @Test
    void emailViolationMapsToEmailAlreadyRegistered() {
        when(userWriter.insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenThrow(violation("users_email_key"));
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void unreadableConstraintFallsBackToSubThenEmail() {
        when(userWriter.insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));
        when(userRepository.findByExternalAuthId(SUB)).thenReturn(Optional.of(saved("x@example.test", false)));
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isInstanceOf(AlreadyRegisteredException.class);

        when(userRepository.findByExternalAuthId(SUB)).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("ada@example.test")).thenReturn(false, true);
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void unexplainedViolationIsRethrown() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("dup");
        when(userWriter.insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenThrow(original);
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isSameAs(original);
    }

    @Test
    void otherNamedConstraintIsRethrown() {
        DataIntegrityViolationException original = violation("chk_something");
        when(userWriter.insert(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .thenThrow(original);
        assertThatThrownBy(() -> service.register(jwt(SUB, null, null), request("ada@example.test")))
                .isSameAs(original);
    }

    @Test
    void getProfileReturnsActiveUser() {
        User user = saved("ada@example.test", true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        assertThat(service.getProfile(user.getId()).id()).isEqualTo(user.getId());
    }

    @Test
    void getProfileRejectsMissingAndSoftDeleted() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProfile(missing)).isInstanceOf(UserNotFoundException.class);

        User deleted = saved("d@example.test", false);
        deleted.setDeletedAt(Instant.now());
        when(userRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.getProfile(deleted.getId())).isInstanceOf(UserNotFoundException.class);
    }
}
