package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class EmailMismatchException extends ApiException {

    public EmailMismatchException() {
        this("Email does not match the authenticated identity");
    }

    public EmailMismatchException(String message) {
        super(ErrorCode.EMAIL_MISMATCH, HttpStatus.BAD_REQUEST, message);
    }
}
