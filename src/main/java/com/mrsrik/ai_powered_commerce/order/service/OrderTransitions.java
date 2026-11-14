package com.mrsrik.ai_powered_commerce.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderItemRepository;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;

/**
 * The single component that applies status transitions to locked order rows (Requirements 4.14, 4.15, 4.16, 4.18, 4.19, 4.20, 4.23).
 *
 * <p>Deliberately has no transaction boundary of its own: callers must hold the order row lock
 * and manage the transaction. Never catches exceptions so that any failure triggers full transaction rollback.
 */
@Component
public class OrderTransitions {

    private final OrderStateMachine stateMachine;
    private final Clock clock;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderTransitions(OrderStateMachine stateMachine,
                            Clock clock,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository,
                            OrderRepository orderRepository) {
        this.stateMachine = stateMachine;
        this.clock = clock;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Applies a status transition to an already-locked Order.
     *
     * <ol>
     *   <li>Validates transition via {@link OrderStateMachine#requireTransition(OrderStatus, OrderStatus)} (throws 409 before mutation).</li>
     *   <li>If transitioning to {@code SHIPPED}, records {@code shippedAt} from the clock if not already set.</li>
     *   <li>If transitioning to {@code CANCELLED}, atomically restocks order items sorted by {@code productId}.</li>
     *   <li>Updates order status and persists the order entity.</li>
     * </ol>
     */
    public void apply(Order lockedOrder, OrderStatus target) {
        Objects.requireNonNull(lockedOrder, "lockedOrder must not be null");
        Objects.requireNonNull(target, "target must not be null");

        // 1. Guard against illegal or same-state transitions
        stateMachine.requireTransition(lockedOrder.getStatus(), target);

        // 2. Set shippedAt when transitioning to SHIPPED (never overwrite if already set)
        if (target == OrderStatus.SHIPPED && lockedOrder.getShippedAt() == null) {
            lockedOrder.setShippedAt(Instant.now(clock));
        }

        // 3. Atomically restock inventory if cancelling
        if (target == OrderStatus.CANCELLED) {
            List<OrderItem> items = orderItemRepository
                    .findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(lockedOrder.getId());

            List<OrderItem> sortedItems = items.stream()
                    .sorted(Comparator.comparing(OrderItem::getProductId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            for (OrderItem item : sortedItems) {
                if (item.getProductId() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                    productRepository.incrementStock(item.getProductId(), item.getQuantity());
                }
            }
        }

        // 4. Update status and save
        lockedOrder.setStatus(target);
        orderRepository.save(lockedOrder);
    }
}

