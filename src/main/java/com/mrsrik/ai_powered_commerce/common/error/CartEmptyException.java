package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class CartEmptyException extends ApiException {

    public CartEmptyException() {
        this("Cart is empty");
    }

    public CartEmptyException(String message) {
        super(ErrorCode.CART_EMPTY, HttpStatus.CONFLICT, message);
    }
}
