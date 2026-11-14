package com.mrsrik.ai_powered_commerce.order.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.common.error.ConstraintNames;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintViolations;
import com.mrsrik.ai_powered_commerce.common.error.OrderNotFoundException;
import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.order.dto.CheckoutRequest;
import com.mrsrik.ai_powered_commerce.order.dto.OrderDetailDto;
import com.mrsrik.ai_powered_commerce.order.dto.OrderSummaryDto;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderItemRepository;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;

/**
 * Implementation of OrderService managing checkout, order reads, transitions, and cancellations.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderCheckoutExecutor checkoutExecutor;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final OrderTransitions orderTransitions;

    public OrderServiceImpl(OrderCheckoutExecutor checkoutExecutor,
                            OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderMapper orderMapper,
                            OrderTransitions orderTransitions) {
        this.checkoutExecutor = checkoutExecutor;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderMapper = orderMapper;
        this.orderTransitions = orderTransitions;
    }

    @Override
    public OrderDetailDto checkout(UUID userId, CheckoutRequest request) {
        try {
            return checkoutExecutor.executeCheckout(userId, request);
        } catch (DataIntegrityViolationException e) {
            boolean isTxnIdCollision = ConstraintViolations.constraintName(e)
                    .map(ConstraintNames.ORDERS_TRANSACTION_ID::equals)
                    .orElse(false);

            if (isTxnIdCollision) {
                // Rare collision on uq_orders_transaction_id: retry whole checkout ONCE in fresh transaction
                return checkoutExecutor.executeCheckout(userId, request);
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryDto> listOrders(UUID userId, PageParams page) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = page.toPageable(sort);
        Page<Order> orderPage = orderRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        return PageResponse.from(orderPage.map(orderMapper::toSummaryDto));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailDto getOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserIdAndDeletedAtIsNull(orderId, userId)
                .orElseThrow(OrderNotFoundException::new);
        List<OrderItem> items = orderItemRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(order.getId());
        return orderMapper.toDetailDto(order, items);
    }

    @Override
    @Transactional
    public OrderDetailDto transition(UUID orderId, OrderStatus target) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        orderTransitions.apply(order, target);
        List<OrderItem> items = orderItemRepository
                .findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(order.getId());
        return orderMapper.toDetailDto(order, items);
    }

    @Override
    @Transactional
    public OrderDetailDto cancelOwn(UUID userId, UUID orderId) {
        Order order = orderRepository.findForUpdateByIdAndUserId(orderId, userId)
                .orElseThrow(OrderNotFoundException::new);
        orderTransitions.apply(order, OrderStatus.CANCELLED);
        List<OrderItem> items = orderItemRepository
                .findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(order.getId());
        return orderMapper.toDetailDto(order, items);
    }
}

