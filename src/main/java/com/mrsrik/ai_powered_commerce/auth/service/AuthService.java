package com.mrsrik.ai_powered_commerce.auth.service;

import org.springframework.stereotype.Service;

import com.mrsrik.ai_powered_commerce.auth.client.Auth0Client;
import com.mrsrik.ai_powered_commerce.auth.client.Auth0SignupResponse;
import com.mrsrik.ai_powered_commerce.auth.dto.AuthTokenResponse;
import com.mrsrik.ai_powered_commerce.auth.dto.LoginRequest;
import com.mrsrik.ai_powered_commerce.auth.dto.SignupRequest;
import com.mrsrik.ai_powered_commerce.common.error.EmailAlreadyRegisteredException;
import com.mrsrik.ai_powered_commerce.user.dto.RegisterUserRequest;
import com.mrsrik.ai_powered_commerce.user.dto.UserProfileDto;
import com.mrsrik.ai_powered_commerce.user.repository.UserRepository;
import com.mrsrik.ai_powered_commerce.user.service.EmailNormalizer;
import com.mrsrik.ai_powered_commerce.user.service.UserRegistrationService;

/**
 * Orchestrates user signup and login against Auth0 and the local database.
 */
@Service
public class AuthService {

    private final Auth0Client auth0Client;
    private final UserRegistrationService userRegistrationService;
    private final UserRepository userRepository;

    public AuthService(Auth0Client auth0Client,
                       UserRegistrationService userRegistrationService,
                       UserRepository userRepository) {
        this.auth0Client = auth0Client;
        this.userRegistrationService = userRegistrationService;
        this.userRepository = userRepository;
    }

    /**
     * Creates Auth0 identity and provisions local user profile.
     * Derives externalAuthId directly from Auth0 _id ("auth0|" + _id).
     */
    public UserProfileDto signup(SignupRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        // 1. Create user in Auth0
        String fullName = (request.firstName() + " " + request.lastName()).trim();
        Auth0SignupResponse signupResponse = auth0Client.createUser(request.email(), request.password(), fullName);

        // 2. Derive externalAuthId: "auth0|" + _id
        String externalAuthId = "auth0|" + signupResponse.id();

        // 3. Register local user profile
        RegisterUserRequest registerRequest = new RegisterUserRequest(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.role()
        );
        return userRegistrationService.register(
                externalAuthId,
                registerRequest,
                signupResponse.emailVerified()
        );
    }

    /**
     * Authenticates user via Auth0 Resource Owner Password Grant.
     */
    public AuthTokenResponse login(LoginRequest request) {
        return auth0Client.getToken(request.email(), request.password());
    }
}
