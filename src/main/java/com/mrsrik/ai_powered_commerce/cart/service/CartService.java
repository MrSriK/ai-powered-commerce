package com.mrsrik.ai_powered_commerce.cart.service;

import java.util.UUID;

import com.mrsrik.ai_powered_commerce.cart.dto.AddCartItemRequest;
import com.mrsrik.ai_powered_commerce.cart.dto.CartDto;
import com.mrsrik.ai_powered_commerce.cart.dto.UpdateCartItemRequest;

public interface CartService {

    CartDto getCart(UUID userId);

    CartDto addItem(UUID userId, AddCartItemRequest request);

    CartDto updateItem(UUID userId, UUID itemId, UpdateCartItemRequest request);

    CartDto removeItem(UUID userId, UUID itemId);
}

