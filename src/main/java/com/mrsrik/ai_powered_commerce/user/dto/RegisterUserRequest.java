package com.mrsrik.ai_powered_commerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration request. It has no role component, so a client can never choose a role (Requirement 13.2).
 * Strings are trimmed before validation, so whitespace-only values fail {@code @NotBlank}. A blank phone number
 * becomes null. Email case is handled later by {@code EmailNormalizer}.
 */
public record RegisterUserRequest(
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 15) String phoneNumber) {

    public RegisterUserRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        email = trim(email);
        phoneNumber = trim(phoneNumber);
        if (phoneNumber != null && phoneNumber.isEmpty()) {
            phoneNumber = null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
