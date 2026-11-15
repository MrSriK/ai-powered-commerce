package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class Auth0ConflictException extends ApiException {
    public Auth0ConflictException() {
        super(ErrorCode.ALREADY_REGISTERED, HttpStatus.CONFLICT, "A user with this email is already registered");
    }
}
