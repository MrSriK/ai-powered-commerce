package com.mrsrik.ai_powered_commerce.common.error;

import org.springframework.http.HttpStatus;

/** Product is inactive or soft-deleted: 404 in the cart context, 409 at checkout (OQ-11). */
public class ProductUnavailableException extends ApiException {

    private static final String MESSAGE = "Product is not available";

    private ProductUnavailableException(HttpStatus status) {
        super(ErrorCode.PRODUCT_UNAVAILABLE, status, MESSAGE);
    }

    public static ProductUnavailableException inCart() {
        return new ProductUnavailableException(HttpStatus.NOT_FOUND);
    }

    public static ProductUnavailableException atCheckout() {
        return new ProductUnavailableException(HttpStatus.CONFLICT);
    }
}
