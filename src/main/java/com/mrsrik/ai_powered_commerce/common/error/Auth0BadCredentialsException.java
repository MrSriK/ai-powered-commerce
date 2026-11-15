package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class Auth0BadCredentialsException extends ApiException {
    public Auth0BadCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
