package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException() {
        this("Order status transition is not allowed");
    }

    public InvalidStatusTransitionException(String message) {
        super(ErrorCode.INVALID_STATUS_TRANSITION, HttpStatus.CONFLICT, message);
    }
}
