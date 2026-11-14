package com.mrsrik.ai_powered_commerce.order.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;

/**
 * Implementation of OrderStepExecutor executing single-order status progression transitions in REQUIRES_NEW transactions.
 */
@Component
public class OrderStepExecutorImpl implements OrderStepExecutor {

    private final OrderRepository orderRepository;
    private final OrderTransitions orderTransitions;

    public OrderStepExecutorImpl(OrderRepository orderRepository, OrderTransitions orderTransitions) {
        this.orderRepository = orderRepository;
        this.orderTransitions = orderTransitions;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StepOutcome advanceOne(UUID orderId, OrderStatus expectedFrom) {
        Optional<Order> orderOpt = orderRepository.findForUpdateById(orderId);
        if (orderOpt.isEmpty()) {
            return StepOutcome.SKIPPED;
        }

        Order order = orderOpt.get();
        if (order.getStatus() != expectedFrom) {
            return StepOutcome.SKIPPED;
        }

        OrderStatus target = switch (expectedFrom) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.DELIVERED;
            default -> throw new IllegalArgumentException("No progression step from status: " + expectedFrom);
        };

        orderTransitions.apply(order, target);
        return StepOutcome.ADVANCED;
    }
}
