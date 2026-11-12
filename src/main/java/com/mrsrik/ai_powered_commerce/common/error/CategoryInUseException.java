package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class CategoryInUseException extends ApiException {

    public CategoryInUseException() {
        this("Category is still used by products");
    }

    public CategoryInUseException(String message) {
        super(ErrorCode.CATEGORY_IN_USE, HttpStatus.CONFLICT, message);
    }
}
