package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class DuplicateCategoryNameException extends ApiException {

    public DuplicateCategoryNameException() {
        this("A category with this name already exists");
    }

    public DuplicateCategoryNameException(String message) {
        super(ErrorCode.DUPLICATE_CATEGORY_NAME, HttpStatus.CONFLICT, message);
    }
}
