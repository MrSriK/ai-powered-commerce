package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class InvalidPaginationException extends ApiException {

    public InvalidPaginationException() {
        this("Invalid pagination parameters");
    }

    public InvalidPaginationException(String message) {
        super(ErrorCode.INVALID_PAGINATION, HttpStatus.BAD_REQUEST, message);
    }
}
