package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException() {
        this("Product not found");
    }

    public ProductNotFoundException(String message) {
        super(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
