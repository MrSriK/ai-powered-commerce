package com.mrsrik.ai_powered_commerce.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class ApiExceptionMappingTest {

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(new EmptyMessageException(), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED),
                Arguments.of(new InvalidPaginationException(), HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION),
                Arguments.of(new EmailMismatchException(), HttpStatus.BAD_REQUEST, ErrorCode.EMAIL_MISMATCH),
                Arguments.of(new ProductNotFoundException(), HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND),
                Arguments.of(new CategoryNotFoundException(), HttpStatus.NOT_FOUND, ErrorCode.CATEGORY_NOT_FOUND),
                Arguments.of(new CartItemNotFoundException(), HttpStatus.NOT_FOUND, ErrorCode.CART_ITEM_NOT_FOUND),
                Arguments.of(new OrderNotFoundException(), HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND),
                Arguments.of(new ConversationNotFoundException(), HttpStatus.NOT_FOUND,
                        ErrorCode.CONVERSATION_NOT_FOUND),
                Arguments.of(new UserNotFoundException(), HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND),
                Arguments.of(ProductUnavailableException.inCart(), HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_UNAVAILABLE),
                Arguments.of(ProductUnavailableException.atCheckout(), HttpStatus.CONFLICT,
                        ErrorCode.PRODUCT_UNAVAILABLE),
                Arguments.of(new DuplicateSkuException(), HttpStatus.CONFLICT, ErrorCode.DUPLICATE_SKU),
                Arguments.of(new DuplicateCategoryNameException(), HttpStatus.CONFLICT,
                        ErrorCode.DUPLICATE_CATEGORY_NAME),
                Arguments.of(new CategoryInUseException(), HttpStatus.CONFLICT, ErrorCode.CATEGORY_IN_USE),
                Arguments.of(new ProductReactivationNotSupportedException(), HttpStatus.CONFLICT,
                        ErrorCode.PRODUCT_REACTIVATION_NOT_SUPPORTED),
                Arguments.of(new AlreadyRegisteredException(), HttpStatus.CONFLICT, ErrorCode.ALREADY_REGISTERED),
                Arguments.of(new EmailAlreadyRegisteredException(), HttpStatus.CONFLICT,
                        ErrorCode.EMAIL_ALREADY_REGISTERED),
                Arguments.of(new InsufficientStockException(), HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK),
                Arguments.of(new CartEmptyException(), HttpStatus.CONFLICT, ErrorCode.CART_EMPTY),
                Arguments.of(new InvalidStatusTransitionException(), HttpStatus.CONFLICT,
                        ErrorCode.INVALID_STATUS_TRANSITION),
                Arguments.of(new OrderTotalTooLargeException(), HttpStatus.CONFLICT, ErrorCode.ORDER_TOTAL_TOO_LARGE),
                Arguments.of(new AiProviderException(), HttpStatus.BAD_GATEWAY, ErrorCode.AI_PROVIDER_UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("table")
    void carriesStatusAndCodeFromDesignTable(ApiException ex, HttpStatus status, ErrorCode code) {
        assertEquals(status, ex.getStatus());
        assertEquals(code, ex.getCode());
    }

    @Test
    void customMessageIsKept() {
        assertEquals("custom", new DuplicateSkuException("custom").getMessage());
    }
}
