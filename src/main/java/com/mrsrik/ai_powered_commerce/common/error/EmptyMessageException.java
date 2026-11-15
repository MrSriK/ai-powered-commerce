package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an AI chat message is empty or whitespace-only (Requirement 7.7).
 */
public class EmptyMessageException extends ApiException {

    public EmptyMessageException() {
        this("Message cannot be blank");
    }

    public EmptyMessageException(String message) {
        super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }
}
