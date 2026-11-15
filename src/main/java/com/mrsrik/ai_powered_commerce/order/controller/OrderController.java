package com.mrsrik.ai_powered_commerce.order.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.order.dto.CheckoutRequest;
import com.mrsrik.ai_powered_commerce.order.dto.OrderDetailDto;
import com.mrsrik.ai_powered_commerce.order.dto.OrderSummaryDto;
import com.mrsrik.ai_powered_commerce.order.dto.UpdateOrderStatusRequest;
import com.mrsrik.ai_powered_commerce.order.service.OrderService;
import com.mrsrik.ai_powered_commerce.security.AuthenticatedUser;
import com.mrsrik.ai_powered_commerce.security.CurrentUser;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;

/**
 * Controller exposing customer checkout, order reads, admin status transitions, and cancellations
 * (Requirements 4.1, 4.8, 4.10, 4.11, 4.12, 4.13, 4.14, 4.15, 4.16, 4.21, 4.22, 2.6, 8.7).
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private UUID getUserId(AuthenticatedUser user) {
        return user != null ? user.userId() : CurrentUser.id();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailDto checkout(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(getUserId(user), request);
    }

    @GetMapping
    public PageResponse<OrderSummaryDto> listOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return orderService.listOrders(getUserId(user), PageParams.of(page, size));
    }

    @GetMapping("/{id}")
    public OrderDetailDto getOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {
        return orderService.getOrder(getUserId(user), id);
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status", description = "Requires ADMIN role")
    public OrderDetailDto updateStatus(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.transition(id, request.status());
    }

    @PostMapping(path = "/{id}/cancel")
    public OrderDetailDto cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable UUID id) {
        return orderService.cancelOwn(getUserId(user), id);
    }
}


