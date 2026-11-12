package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateSkuException extends ApiException {

    public DuplicateSkuException() {
        this("A product with this SKU already exists");
    }

    public DuplicateSkuException(String message) {
        super(ErrorCode.DUPLICATE_SKU, HttpStatus.CONFLICT, message);
    }
}
