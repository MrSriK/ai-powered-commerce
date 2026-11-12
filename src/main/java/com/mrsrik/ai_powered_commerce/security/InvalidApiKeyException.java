package com.mrsrik.ai_powered_commerce.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown by the internal API-key filter when the presented key is missing or wrong.
 * The message is fixed and never echoes the key.
 */
public class InvalidApiKeyException extends AuthenticationException {

    private static final String MESSAGE = "Invalid API key";

    public InvalidApiKeyException() {
        super(MESSAGE);
    }
}
