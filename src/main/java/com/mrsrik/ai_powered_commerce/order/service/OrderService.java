package com.mrsrik.ai_powered_commerce.order.service;

import java.util.UUID;

import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.order.dto.CheckoutRequest;
import com.mrsrik.ai_powered_commerce.order.dto.OrderDetailDto;
import com.mrsrik.ai_powered_commerce.order.dto.OrderSummaryDto;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;

/**
 * Service contract for order management and checkout operations (Requirements 4.1 to 4.23).
 */
public interface OrderService {

    /**
     * Checks out the customer's shopping cart into a PENDING order with snapshot fields and decremented stock.
     */
    OrderDetailDto checkout(UUID userId, CheckoutRequest request);

    /**
     * Returns a paginated list of orders owned by the specified user, ordered by createdAt DESC, id DESC.
     */
    PageResponse<OrderSummaryDto> listOrders(UUID userId, PageParams page);

    /**
     * Returns the order detail DTO with snapshots and items for an order owned by the specified user.
     * Throws OrderNotFoundException (404 ORDER_NOT_FOUND) if the order does not exist or belongs to another user.
     */
    OrderDetailDto getOrder(UUID userId, UUID orderId);

    /**
     * Admin operation: transitions any order to the target status.
     * When target is CANCELLED, restores item stocks atomically.
     * Throws OrderNotFoundException (404 ORDER_NOT_FOUND) if the order does not exist or is soft-deleted.
     * Throws InvalidStatusTransitionException (409 INVALID_STATUS_TRANSITION) if transition is illegal.
     */
    OrderDetailDto transition(UUID orderId, OrderStatus target);

    /**
     * Customer operation: cancels own order and restores item stocks atomically.
     * Throws OrderNotFoundException (404 ORDER_NOT_FOUND) if the order does not exist, is soft-deleted,
     * or belongs to another user.
     * Throws InvalidStatusTransitionException (409 INVALID_STATUS_TRANSITION) if order is DELIVERED or CANCELLED.
     */
    OrderDetailDto cancelOwn(UUID userId, UUID orderId);
}


