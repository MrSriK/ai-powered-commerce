package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

/** Base of all domain exceptions; the message is sent to clients, so it must stay free of internals. */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    protected ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    protected ApiException(ErrorCode code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
