package com.mrsrik.ai_powered_commerce.user.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.common.error.AlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintNames;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintViolations;
import com.mrsrik.ai_powered_commerce.common.error.EmailAlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.common.error.EmailMismatchException;
import com.mrsrik.ai_powered_commerce.common.error.UserNotFoundException;
import com.mrsrik.ai_powered_commerce.user.dto.RegisterUserRequest;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

/** Self-registration of CUSTOMER users and profile lookup (Requirement 13). */
@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final UserWriter userWriter;

    public UserRegistrationService(UserRepository userRepository, UserWriter userWriter) {
        this.userRepository = userRepository;
        this.userWriter = userWriter;
    }

    /**
     * Registers the caller identified by {@code jwt}. Deliberately NOT {@code @Transactional}: after a unique
     * violation PostgreSQL marks the current transaction aborted, so no further query can run in it. The insert
     * runs in {@link UserWriter}'s own transaction, and the violation is decided here, outside it.
     *
     * <p>Checks run in order: token email cross-check, subject already registered, email already registered
     * (both including soft-deleted users). The pre-checks are a fast path; the unique constraints decide races.
     */
    public UserProfileDto register(Jwt jwt, RegisterUserRequest request) {
        String sub = jwt.getSubject();
        String email = EmailNormalizer.normalize(request.email());
        String tokenEmail = jwt.getClaimAsString("email");
        boolean hasTokenEmail = tokenEmail != null && !tokenEmail.isBlank();
        boolean tokenEmailMatches = hasTokenEmail && email.equals(EmailNormalizer.normalize(tokenEmail));

        if (hasTokenEmail && !tokenEmailMatches) {
            throw new EmailMismatchException();
        }
        boolean emailVerified = tokenEmailMatches && isTrue(jwt.getClaim("email_verified"));
        return register(sub, request, emailVerified);
    }

    /**
     * Registers a user by externalAuthId with a specific role (e.g. from backend Auth0 signup flow).
     */
    public UserProfileDto register(String externalAuthId, RegisterUserRequest request, boolean emailVerified) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByExternalAuthId(externalAuthId)) {
            throw new AlreadyRegisteredException();
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        try {
            User saved = userWriter.insert(externalAuthId, request.firstName(), request.lastName(), email,
                    request.phoneNumber(), emailVerified, request.role());
            return UserProfileDto.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw translate(e, externalAuthId, email);
        }
    }

    /** Returns the profile of an active user; a missing or soft-deleted user is a 404 (Requirement 13.10). */
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .map(UserProfileDto::from)
                .orElseThrow(UserNotFoundException::new);
    }

    /** Picks the 409 from the violated constraint; unknown violations are rethrown (500). */
    private RuntimeException translate(DataIntegrityViolationException e, String sub, String email) {
        Optional<String> constraint = ConstraintViolations.constraintName(e);
        if (constraint.isPresent()) {
            return switch (constraint.get()) {
                case ConstraintNames.USERS_EXTERNAL_AUTH_ID -> new AlreadyRegisteredException();
                case ConstraintNames.USERS_EMAIL -> new EmailAlreadyRegisteredException();
                default -> e;
            };
        }
        // Constraint name unreadable: re-read by sub first, then by email.
        if (userRepository.findByExternalAuthId(sub).isPresent()) {
            return new AlreadyRegisteredException();
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return new EmailAlreadyRegisteredException();
        }
        return e;
    }

    /** Supports a Boolean claim (Auth0) and the String "true" (case-insensitive) some providers send. */
    private static boolean isTrue(Object claim) {
        if (claim instanceof Boolean b) {
            return b;
        }
        return claim instanceof String s && "true".equalsIgnoreCase(s.trim());
    }
}
