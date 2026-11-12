package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class ProductReactivationNotSupportedException extends ApiException {

    public ProductReactivationNotSupportedException() {
        this("An inactive product cannot be reactivated");
    }

    public ProductReactivationNotSupportedException(String message) {
        super(ErrorCode.PRODUCT_REACTIVATION_NOT_SUPPORTED, HttpStatus.CONFLICT, message);
    }
}
