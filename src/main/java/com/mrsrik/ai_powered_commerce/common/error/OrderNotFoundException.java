package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException() {
        this("Order not found");
    }

    public OrderNotFoundException(String message) {
        super(ErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
