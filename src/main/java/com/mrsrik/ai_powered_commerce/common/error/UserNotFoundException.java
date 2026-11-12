package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        this("User not found");
    }

    public UserNotFoundException(String message) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
