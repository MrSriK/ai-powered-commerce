package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class AlreadyRegisteredException extends ApiException {

    public AlreadyRegisteredException() {
        this("This identity is already registered");
    }

    public AlreadyRegisteredException(String message) {
        super(ErrorCode.ALREADY_REGISTERED, HttpStatus.CONFLICT, message);
    }
}
