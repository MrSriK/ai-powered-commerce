package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException() {
        this("This email is already registered");
    }

    public EmailAlreadyRegisteredException(String message) {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT, message);
    }
}
