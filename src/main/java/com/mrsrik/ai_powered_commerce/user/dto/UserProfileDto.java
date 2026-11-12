package com.mrsrik.ai_powered_commerce.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

/** Public view of a user. It never exposes the external auth id or the deletion timestamp. */
public record UserProfileDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        boolean emailVerified,
        Role role,
        Instant createdAt) {

    /** Maps a user entity to its profile view. */
    public static UserProfileDto from(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.isEmailVerified(),
                user.getRole(),
                user.getCreatedAt());
    }
}
