package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

public class OrderTotalTooLargeException extends ApiException {

    public OrderTotalTooLargeException() {
        this("Order total is too large");
    }

    public OrderTotalTooLargeException(String message) {
        super(ErrorCode.ORDER_TOTAL_TOO_LARGE, HttpStatus.CONFLICT, message);
    }
}
