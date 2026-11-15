package com.mrsrik.ai_powered_commerce.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.auth.dto.AuthTokenResponse;
import com.mrsrik.ai_powered_commerce.auth.dto.LoginRequest;
import com.mrsrik.ai_powered_commerce.auth.dto.SignupRequest;
import com.mrsrik.ai_powered_commerce.auth.service.AuthService;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Authentication endpoints implementing the Auth0 Resource Owner Password Grant flow.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration and login via Resource Owner Password Grant")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account in Auth0 and the local database.
     */
    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register a new user account",
            description = "Creates user in Auth0, provisions local profile, and returns the created user profile.")
    public UserProfileDto signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    /**
     * Authenticates an existing user and returns an access token.
     */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    @Operation(summary = "Log in with email and password",
            description = "Authenticates user credentials via Auth0 Resource Owner Password Grant and returns an access token.")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
