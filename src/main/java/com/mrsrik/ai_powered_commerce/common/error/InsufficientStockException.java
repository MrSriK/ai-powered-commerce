package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException() {
        this("Insufficient stock");
    }

    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, message);
    }
}
