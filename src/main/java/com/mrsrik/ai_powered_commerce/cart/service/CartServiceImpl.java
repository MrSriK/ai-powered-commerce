package com.mrsrik.ai_powered_commerce.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.cart.dto.AddCartItemRequest;
import com.mrsrik.ai_powered_commerce.cart.dto.CartDto;
import com.mrsrik.ai_powered_commerce.cart.dto.CartItemDto;
import com.mrsrik.ai_powered_commerce.cart.dto.UpdateCartItemRequest;
import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;
import com.mrsrik.ai_powered_commerce.cart.repository.CartItemRepository;
import com.mrsrik.ai_powered_commerce.cart.repository.CartRepository;
import com.mrsrik.ai_powered_commerce.common.error.CartItemNotFoundException;
import com.mrsrik.ai_powered_commerce.common.error.InsufficientStockException;
import com.mrsrik.ai_powered_commerce.common.error.ProductUnavailableException;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartWriter cartWriter;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            CartWriter cartWriter) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.cartWriter = cartWriter;
    }

    @Override
    public CartDto getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return buildCartDto(cart.getId());
    }

    @Override
    public CartDto addItem(UUID userId, AddCartItemRequest request) {
        Cart cart = getOrCreateAndLock(userId);

        Product product = productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(request.productId())
                .orElseThrow(ProductUnavailableException::inCart);

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        int targetQuantity;
        if (existingItem.isPresent()) {
            targetQuantity = Math.min(existingItem.get().getQuantity() + request.quantity(), 999);
        } else {
            targetQuantity = Math.min(request.quantity(), 999);
        }

        if (targetQuantity > product.getStock()) {
            throw new InsufficientStockException();
        }

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(targetQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cartId(cart.getId())
                    .productId(product.getId())
                    .cart(cart)
                    .product(product)
                    .quantity(targetQuantity)
                    .build();
            cartItemRepository.save(newItem);
        }

        return buildCartDto(cart.getId());
    }

    @Override
    public CartDto updateItem(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateAndLock(userId);

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(CartItemNotFoundException::new);

        Product product = productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(item.getProductId())
                .orElseThrow(ProductUnavailableException::inCart);

        if (request.quantity() > product.getStock()) {
            throw new InsufficientStockException();
        }

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        return buildCartDto(cart.getId());
    }

    @Override
    public CartDto removeItem(UUID userId, UUID itemId) {
        Cart cart = getOrCreateAndLock(userId);

        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(CartItemNotFoundException::new);

        cartItemRepository.delete(item);
        cartItemRepository.flush();

        return buildCartDto(cart.getId());
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseGet(() -> {
                    try {
                        return cartWriter.createCart(userId);
                    } catch (DataIntegrityViolationException e) {
                        return cartRepository.findByUserIdAndDeletedAtIsNull(userId)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private Cart getOrCreateAndLock(UUID userId) {
        Optional<Cart> locked = cartRepository.findForUpdateByUserId(userId);
        if (locked.isPresent()) {
            return locked.get();
        }
        try {
            cartWriter.createCart(userId);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent creation won
        }
        return cartRepository.findForUpdateByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Cart not found after creation for user " + userId));
    }

    private CartDto buildCartDto(UUID cartId) {
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        List<CartItemDto> itemDtos = items.stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal unitPrice = product != null ? product.getSellingPrice() : BigDecimal.ZERO;
                    String productName = product != null ? product.getName() : "";
                    String brand = product != null ? product.getBrand() : "";
                    BigDecimal subtotal = CartTotals.computeSubtotal(unitPrice, item.getQuantity());

                    return new CartItemDto(
                            item.getId(),
                            item.getProductId(),
                            productName,
                            brand,
                            item.getQuantity(),
                            unitPrice,
                            subtotal);
                })
                .toList();

        CartTotals totals = CartTotals.of(itemDtos);
        return new CartDto(cartId, itemDtos, totals.totalItems(), totals.totalAmount());
    }
}
