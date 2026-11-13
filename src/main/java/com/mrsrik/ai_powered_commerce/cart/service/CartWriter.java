package com.mrsrik.ai_powered_commerce.cart.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.repository.CartRepository;

/**
 * Creates a cart in its own short transaction. Separate bean with REQUIRES_NEW so that
 * unique constraint violations on carts.user_id can be caught and handled cleanly
 * without marking the outer transaction rollback-only.
 */
@Service
public class CartWriter {

    private final CartRepository cartRepository;

    public CartWriter(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cart createCart(UUID userId) {
        Cart cart = Cart.builder()
                .userId(userId)
                .build();
        return cartRepository.saveAndFlush(cart);
    }
}

