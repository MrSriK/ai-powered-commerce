package com.mrsrik.ai_powered_commerce.user.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.security.CurrentUser;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.service.UserRegistrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Caller's own profile (Requirement 13). */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile operations")
public class UserController {

    private final UserRegistrationService registrationService;

    public UserController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Returns the caller's profile; the security chain already limits this route to registered users. */
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get current user profile", description = "Returns the profile of the authenticated caller.")
    public UserProfileDto me() {
        return registrationService.getProfile(CurrentUser.id());
    }
}
