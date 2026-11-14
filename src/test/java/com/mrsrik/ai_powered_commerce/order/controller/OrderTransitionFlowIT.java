package com.mrsrik.ai_powered_commerce.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates admin status transitions and customer cancellations (Requirements 4.14, 4.15, 4.16, 4.18, 4.19, 4.20, 4.21, 4.22, 4.23, 2.6, 8.7):
 * 1. Admin forward transitions: PENDING -> CONFIRMED -> SHIPPED (records shippedAt) -> DELIVERED.
 * 2. Admin illegal transitions return 409 INVALID_STATUS_TRANSITION and leave status unchanged.
 * 3. Role gate: Customer calling PATCH /api/orders/{id}/status receives 403 FORBIDDEN.
 * 4. Admin can cancel any order via PATCH status = CANCELLED and items are restocked.
 * 5. Customer can cancel own PENDING, CONFIRMED, or SHIPPED order via POST /cancel and stock is restored.
 * 6. Customer cancelling foreign order returns 404 ORDER_NOT_FOUND with no stock/order changes.
 * 7. Customer cancelling DELIVERED or CANCELLED order returns 409 INVALID_STATUS_TRANSITION with no stock changes.
 * 8. Cancellation safely restocks soft-deleted products and skips null product_id items.
 * 9. Non-existent order returns 404 ORDER_NOT_FOUND.
 */
class OrderTransitionFlowIT extends AbstractWebIT {

    private static final String ORDERS_URL = "/api/orders";

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private String statusPayload(String status) {
        return """
                {
                    "status": "%s"
                }
                """.formatted(status);
    }

    @Test
    @DisplayName("Admin can advance order PENDING -> CONFIRMED -> SHIPPED -> DELIVERED and shippedAt is set on SHIPPED")
    void adminForwardTransitions() throws Exception {
        Caller admin = callers.registeredAdmin();
        Caller customer = callers.registeredCustomer();

        Order order = testData.order(customer.userId(), OrderStatus.PENDING);

        // 1. PENDING -> CONFIRMED
        perform(patch(ORDERS_URL + "/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("CONFIRMED")), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.shippedAt", nullValue()));

        // 2. CONFIRMED -> SHIPPED (records shippedAt)
        perform(patch(ORDERS_URL + "/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("SHIPPED")), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SHIPPED")))
                .andExpect(jsonPath("$.shippedAt", notNullValue()));

        Order shippedOrder = orderRepository.findById(order.getId()).orElseThrow();
        Instant originalShippedAt = shippedOrder.getShippedAt();
        assertThat(originalShippedAt).isNotNull();

        // 3. SHIPPED -> DELIVERED (shippedAt remains unchanged)
        perform(patch(ORDERS_URL + "/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("DELIVERED")), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.shippedAt", notNullValue()));

        Order deliveredOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(deliveredOrder.getShippedAt()).isEqualTo(originalShippedAt);
    }

    @Test
    @DisplayName("Admin illegal transitions return 409 INVALID_STATUS_TRANSITION and status remains unchanged")
    void adminIllegalTransitions() throws Exception {
        Caller admin = callers.registeredAdmin();
        Caller customer = callers.registeredCustomer();

        // PENDING -> SHIPPED is illegal (must go through CONFIRMED first)
        Order pendingOrder = testData.order(customer.userId(), OrderStatus.PENDING);
        perform(patch(ORDERS_URL + "/" + pendingOrder.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("SHIPPED")), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));

        Order reloadedPending = orderRepository.findById(pendingOrder.getId()).orElseThrow();
        assertThat(reloadedPending.getStatus()).isEqualTo(OrderStatus.PENDING);

        // DELIVERED is terminal: cannot transition out
        Order deliveredOrder = testData.order(customer.userId(), OrderStatus.DELIVERED);
        perform(patch(ORDERS_URL + "/" + deliveredOrder.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("CANCELLED")), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));

        // Same-state transition PENDING -> PENDING is illegal
        perform(patch(ORDERS_URL + "/" + pendingOrder.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("PENDING")), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    @DisplayName("Role gate: Customer cannot call admin PATCH status endpoint")
    void customerCannotCallStatusEndpoint() throws Exception {
        Caller customer = callers.registeredCustomer();
        Order order = testData.order(customer.userId(), OrderStatus.PENDING);

        perform(patch(ORDERS_URL + "/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("CONFIRMED")), customer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("Admin can cancel any customer order and product stock is restored")
    void adminCanCancelAnyOrderWithRestock() throws Exception {
        Caller admin = callers.registeredAdmin();
        Caller customer = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(50));
        Order order = testData.order(customer.userId(), OrderStatus.CONFIRMED);
        testData.orderItem(order.getId(), product.getId(), 5);

        perform(patch(ORDERS_URL + "/" + order.getId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("CANCELLED")), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(55);
    }

    @Test
    @DisplayName("Customer can cancel own PENDING, CONFIRMED, or SHIPPED order and stock is restored")
    void customerCanCancelOwnOrders() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        // 1. Cancel own PENDING order
        Product prod1 = testData.product(category.getId(), b -> b.stock(20));
        Order orderPending = testData.order(customer.userId(), OrderStatus.PENDING);
        testData.orderItem(orderPending.getId(), prod1.getId(), 3);

        perform(post(ORDERS_URL + "/" + orderPending.getId() + "/cancel"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(productRepository.findById(prod1.getId()).orElseThrow().getStock()).isEqualTo(23);

        // 2. Cancel own CONFIRMED order
        Product prod2 = testData.product(category.getId(), b -> b.stock(10));
        Order orderConfirmed = testData.order(customer.userId(), OrderStatus.CONFIRMED);
        testData.orderItem(orderConfirmed.getId(), prod2.getId(), 4);

        perform(post(ORDERS_URL + "/" + orderConfirmed.getId() + "/cancel"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(productRepository.findById(prod2.getId()).orElseThrow().getStock()).isEqualTo(14);

        // 3. Cancel own SHIPPED order
        Product prod3 = testData.product(category.getId(), b -> b.stock(30));
        Order orderShipped = testData.order(customer.userId(), OrderStatus.SHIPPED);
        testData.orderItem(orderShipped.getId(), prod3.getId(), 2);

        perform(post(ORDERS_URL + "/" + orderShipped.getId() + "/cancel"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(productRepository.findById(prod3.getId()).orElseThrow().getStock()).isEqualTo(32);
    }

    @Test
    @DisplayName("Customer cannot cancel foreign order and receives 404 ORDER_NOT_FOUND with stock untouched")
    void customerCannotCancelForeignOrder() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(50));
        Order orderA = testData.order(customerA.userId(), OrderStatus.PENDING);
        testData.orderItem(orderA.getId(), product.getId(), 5);

        // Customer B tries to cancel Customer A's order
        perform(post(ORDERS_URL + "/" + orderA.getId() + "/cancel"), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));

        // Order A is still PENDING and stock is still 50
        assertThat(orderRepository.findById(orderA.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("Customer cannot cancel DELIVERED or CANCELLED order and receives 409 with stock untouched")
    void customerCannotCancelTerminalOrder() throws Exception {
        Caller customer = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(40));

        // Attempt to cancel DELIVERED order
        Order deliveredOrder = testData.order(customer.userId(), OrderStatus.DELIVERED);
        testData.orderItem(deliveredOrder.getId(), product.getId(), 5);

        perform(post(ORDERS_URL + "/" + deliveredOrder.getId() + "/cancel"), customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));

        assertThat(orderRepository.findById(deliveredOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(40);

        // Attempt to cancel CANCELLED order
        Order cancelledOrder = testData.order(customer.userId(), OrderStatus.CANCELLED);
        testData.orderItem(cancelledOrder.getId(), product.getId(), 5);

        perform(post(ORDERS_URL + "/" + cancelledOrder.getId() + "/cancel"), customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));

        assertThat(orderRepository.findById(cancelledOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(40);
    }

    @Test
    @DisplayName("Cancellation restocks soft-deleted product and skips null productId without error")
    void cancelRestocksSoftDeletedAndSkipsNullProductId() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        Product activeProduct = testData.product(category.getId(), b -> b.stock(10));
        Product softDeletedProduct = testData.product(category.getId(), b -> b.stock(20).deletedAt(Instant.now()));

        Order order = testData.order(customer.userId(), OrderStatus.CONFIRMED);
        testData.orderItem(order.getId(), activeProduct.getId(), 2);
        testData.orderItem(order.getId(), softDeletedProduct.getId(), 3);
        // Order item with null productId
        testData.orderItem(order.getId(), null, 4);

        perform(post(ORDERS_URL + "/" + order.getId() + "/cancel"), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(productRepository.findById(activeProduct.getId()).orElseThrow().getStock()).isEqualTo(12);
        assertThat(productRepository.findById(softDeletedProduct.getId()).orElseThrow().getStock()).isEqualTo(23);
    }

    @Test
    @DisplayName("Non-existent order returns 404 ORDER_NOT_FOUND on admin PATCH and customer cancel")
    void nonExistentOrderReturns404() throws Exception {
        Caller admin = callers.registeredAdmin();
        Caller customer = callers.registeredCustomer();
        UUID randomId = UUID.randomUUID();

        perform(patch(ORDERS_URL + "/" + randomId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusPayload("CONFIRMED")), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));

        perform(post(ORDERS_URL + "/" + randomId + "/cancel"), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));
    }
}

