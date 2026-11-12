package com.mrsrik.ai_powered_commerce.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.security.CurrentUser;
import com.mrsrik.ai_powered_commerce.user.dto.RegisterUserRequest;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.service.UserRegistrationService;

import jakarta.validation.Valid;

/** Self-registration and the caller's own profile (Requirement 13). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRegistrationService registrationService;

    public UserController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Registers the token's identity as a CUSTOMER. Open to any valid token, including an unregistered
     * identity, so it reads the token with {@code CurrentUser.jwt()} and never {@code CurrentUser.id()}.
     */
    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileDto register(@Valid @RequestBody RegisterUserRequest request) {
        return registrationService.register(CurrentUser.jwt(), request);
    }

    /** Returns the caller's profile; the security chain already limits this route to registered users. */
    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserProfileDto me() {
        return registrationService.getProfile(CurrentUser.id());
    }
}
