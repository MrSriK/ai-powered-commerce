package com.mrsrik.ai_powered_commerce.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import com.mrsrik.ai_powered_commerce.order.dto.OrderDetailDto;
import com.mrsrik.ai_powered_commerce.order.dto.OrderItemDto;
import com.mrsrik.ai_powered_commerce.order.dto.OrderSummaryDto;
import com.mrsrik.ai_powered_commerce.order.dto.ShippingAddressDto;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;

/**
 * Mapper between Order/OrderItem entities and DTO records (Requirements 4.2, 4.3, 4.10, 4.12).
 */
@Component
public class OrderMapper {

    public OrderSummaryDto toSummaryDto(Order order) {
        return new OrderSummaryDto(
                order.getId(),
                order.getStatus(),
                order.getTotalItems(),
                order.getTotalAmount(),
                order.getTransactionId(),
                order.getCreatedAt(),
                order.getShippedAt());
    }

    public OrderDetailDto toDetailDto(Order order, List<OrderItem> items) {
        ShippingAddressDto address = new ShippingAddressDto(
                order.getShippingName(),
                order.getShippingPhoneNumber(),
                order.getShippingAddressLine1(),
                order.getShippingAddressLine2(),
                order.getCity(),
                order.getState(),
                order.getPostalCode(),
                order.getCountry());

        List<OrderItemDto> itemDtos = items.stream()
                .map(this::toItemDto)
                .toList();

        return new OrderDetailDto(
                order.getId(),
                order.getStatus(),
                order.getTotalItems(),
                order.getTotalAmount(),
                order.getTransactionId(),
                order.getCreatedAt(),
                order.getShippedAt(),
                address,
                itemDtos);
    }

    public OrderItemDto toItemDto(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice() != null && item.getQuantity() != null
                ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new OrderItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getBrand(),
                item.getSku(),
                item.getUnitPrice(),
                item.getQuantity() != null ? item.getQuantity() : 0,
                subtotal);
    }
}

