package com.mrsrik.ai_powered_commerce.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * Inserts a user in its own short transaction. It is a separate bean so that
 * {@code UserRegistrationService.register}, which holds no transaction, can catch a unique violation after this
 * transaction has already rolled back (Requirement 13.8, D-75).
 */
@Service
public class UserWriter {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public UserWriter(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    /**
     * Persists a user with a specific role, flushes so unique constraints fire here, and refreshes the row so the database-managed
     * {@code createdAt} and {@code updatedAt} are populated. Throws {@code DataIntegrityViolationException} on a
     * unique violation; the transaction is rolled back before the exception reaches the caller.
     */
    @Transactional
    public User insert(String externalAuthId, String firstName, String lastName, String email, String phoneNumber,
            boolean emailVerified, Role role) {
        User user = User.builder()
                .externalAuthId(externalAuthId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .emailVerified(emailVerified)
                .role(role)
                .build();
        // saveAndFlush goes through the repository proxy, which translates the persistence exception
        // into DataIntegrityViolationException (a plain EntityManager call in a @Service would not).
        User saved = userRepository.saveAndFlush(user);
        entityManager.refresh(saved);
        return saved;
    }
}
