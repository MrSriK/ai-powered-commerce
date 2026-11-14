package com.mrsrik.ai_powered_commerce.order.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;
import com.mrsrik.ai_powered_commerce.cart.repository.CartItemRepository;
import com.mrsrik.ai_powered_commerce.cart.repository.CartRepository;
import com.mrsrik.ai_powered_commerce.common.error.CartEmptyException;
import com.mrsrik.ai_powered_commerce.common.error.InsufficientStockException;
import com.mrsrik.ai_powered_commerce.common.error.ProductUnavailableException;
import com.mrsrik.ai_powered_commerce.order.dto.CheckoutRequest;
import com.mrsrik.ai_powered_commerce.order.dto.OrderDetailDto;
import com.mrsrik.ai_powered_commerce.order.dto.ShippingAddressDto;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderItemRepository;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;

/**
 * Handles the single-attempt transactional checkout execution (Requirements 4.1 to 4.9, 4.17).
 *
 * <p>Annotated with {@code REQUIRES_NEW} so each attempt runs in its own clean database transaction.
 * If a transaction ID collision occurs on the order table's UNIQUE constraint, the aborted transaction
 * does not contaminate the caller's retry attempt.
 */
@Component
public class OrderCheckoutExecutor {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TransactionIdGenerator transactionIdGenerator;
    private final OrderMapper orderMapper;
    private final EntityManager entityManager;

    public OrderCheckoutExecutor(CartRepository cartRepository,
                                 CartItemRepository cartItemRepository,
                                 ProductRepository productRepository,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 TransactionIdGenerator transactionIdGenerator,
                                 OrderMapper orderMapper,
                                 EntityManager entityManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.transactionIdGenerator = transactionIdGenerator;
        this.orderMapper = orderMapper;
        this.entityManager = entityManager;
    }

    private record ItemSnapshot(Product product, int quantity) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderDetailDto executeCheckout(UUID userId, CheckoutRequest request) {
        // 1. Lock the user's cart
        Cart cart = cartRepository.findForUpdateByUserId(userId).orElse(null);
        if (cart == null) {
            throw new CartEmptyException();
        }

        // 2. Load lines. If empty, throw CartEmptyException (409 CART_EMPTY)
        List<CartItem> lines = cartItemRepository.findByCartId(cart.getId());
        if (lines.isEmpty()) {
            throw new CartEmptyException();
        }

        // 3. Sort lines by productId. Fixed lock order avoids deadlocks between concurrent checkouts
        List<CartItem> sortedLines = lines.stream()
                .sorted(Comparator.comparing(CartItem::getProductId))
                .toList();

        // 4. For each line, atomically decrement stock
        List<ItemSnapshot> snapshots = new ArrayList<>();
        for (CartItem line : sortedLines) {
            UUID productId = line.getProductId();
            int qty = line.getQuantity();

            int rows = productRepository.decrementStockIfAvailable(productId, qty);
            if (rows == 0) {
                // Re-read product to classify error
                Product product = productRepository.findById(productId).orElse(null);
                if (product == null || product.getDeletedAt() != null || !product.isActive()) {
                    throw ProductUnavailableException.atCheckout();
                }
                throw new InsufficientStockException();
            }

            // rows == 1: read product row (row lock held until commit) and capture snapshot
            Product product = productRepository.findById(productId).orElseThrow();
            snapshots.add(new ItemSnapshot(product, qty));
        }

        // 5. Compute totals with OrderTotals (validates MAX_ORDER_TOTAL <= 9999999999.99)
        List<OrderTotals.Line> totalLines = snapshots.stream()
                .map(s -> new OrderTotals.Line(s.product().getSellingPrice(), s.quantity()))
                .toList();
        OrderTotals totals = OrderTotals.of(totalLines);

        // 6. Generate transactionId
        String transactionId = transactionIdGenerator.generate();

        // 7. Create and save Order
        ShippingAddressDto addr = request.shippingAddress();
        Order order = Order.builder()
                .userId(userId)
                .shippingName(addr.name())
                .shippingPhoneNumber(addr.phoneNumber())
                .shippingAddressLine1(addr.addressLine1())
                .shippingAddressLine2(addr.addressLine2())
                .city(addr.city())
                .state(addr.state())
                .postalCode(addr.postalCode())
                .country(addr.country())
                .transactionId(transactionId)
                .totalAmount(totals.totalAmount())
                .totalItems(totals.totalItems())
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);

        // 8. Create and save OrderItems
        List<OrderItem> savedItems = new ArrayList<>();
        for (ItemSnapshot s : snapshots) {
            OrderItem item = OrderItem.builder()
                    .orderId(savedOrder.getId())
                    .order(savedOrder)
                    .productId(s.product().getId())
                    .productName(s.product().getName())
                    .brand(s.product().getBrand())
                    .sku(s.product().getSku())
                    .unitPrice(s.product().getSellingPrice())
                    .quantity(s.quantity())
                    .build();
            savedItems.add(orderItemRepository.save(item));
        }

        // 9. Hard delete cart lines; cart itself remains
        cartItemRepository.deleteByCartId(cart.getId());

        // 10. Flush and refresh so DB-generated timestamps (createdAt, updatedAt) are populated
        orderRepository.flush();
        orderItemRepository.flush();
        entityManager.refresh(savedOrder);

        return orderMapper.toDetailDto(savedOrder, savedItems);
    }
}

