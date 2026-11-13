package com.mrsrik.ai_powered_commerce.cart.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.cart.dto.AddCartItemRequest;
import com.mrsrik.ai_powered_commerce.cart.dto.CartDto;
import com.mrsrik.ai_powered_commerce.cart.dto.UpdateCartItemRequest;
import com.mrsrik.ai_powered_commerce.cart.service.CartService;
import com.mrsrik.ai_powered_commerce.security.AuthenticatedUser;
import com.mrsrik.ai_powered_commerce.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * Shopping cart endpoints for registered users (Requirement 3).
 */
@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private UUID getUserId(AuthenticatedUser user) {
        return user != null ? user.userId() : CurrentUser.id();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CartDto getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(getUserId(user));
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CartDto addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(getUserId(user), request);
    }

    @PutMapping(path = "/items/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CartDto updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(getUserId(user), itemId, request);
    }

    @DeleteMapping(path = "/items/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CartDto removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itemId) {
        return cartService.removeItem(getUserId(user), itemId);
    }
}

