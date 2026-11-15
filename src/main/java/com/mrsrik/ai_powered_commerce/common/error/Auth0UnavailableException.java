package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class Auth0UnavailableException extends ApiException {
    public Auth0UnavailableException() {
        super(ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is temporarily unavailable");
    }

    public Auth0UnavailableException(Throwable cause) {
        super(ErrorCode.AUTH_TEMPORARILY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is temporarily unavailable", cause);
    }
}
