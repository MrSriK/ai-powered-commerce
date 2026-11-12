package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class CartItemNotFoundException extends ApiException {

    public CartItemNotFoundException() {
        this("Cart item not found");
    }

    public CartItemNotFoundException(String message) {
        super(ErrorCode.CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
