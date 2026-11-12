package com.mrsrik.ai_powered_commerce.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Deliberately includes soft-deleted users: the authentication converter must
     * see them to answer 401 ACCOUNT_DISABLED (Requirement 6.6).
     */
    Optional<User> findByExternalAuthId(String externalAuthId);

    /**
     * Deliberately includes soft-deleted users: their identity stays reserved, so
     * registration must not create a second row for the same subject.
     */
    boolean existsByExternalAuthId(String externalAuthId);

    /**
     * Deliberately includes soft-deleted users: an email stays reserved after
     * soft delete (Requirement 13.4).
     */
    boolean existsByEmailIgnoreCase(String email);
}
