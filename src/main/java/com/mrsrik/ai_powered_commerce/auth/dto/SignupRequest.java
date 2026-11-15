package com.mrsrik.ai_powered_commerce.auth.dto;

import com.mrsrik.ai_powered_commerce.user.enums.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 15, max = 72) String password,
        @Size(max = 15) String phoneNumber,
        @NotNull Role role
) {
    public SignupRequest {
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
