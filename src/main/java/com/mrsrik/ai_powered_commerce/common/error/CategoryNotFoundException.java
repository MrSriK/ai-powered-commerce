package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {

    public CategoryNotFoundException() {
        this("Category not found");
    }

    public CategoryNotFoundException(String message) {
        super(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
