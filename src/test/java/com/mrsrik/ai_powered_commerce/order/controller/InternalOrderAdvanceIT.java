package com.mrsrik.ai_powered_commerce.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;
import com.mrsrik.ai_powered_commerce.user.entity.User;

/**
 * Validates the internal order progression endpoint and credential separation (Requirements 12.2, 12.6, 12.7, 12.10, 12.11):
 * 1. POST /internal/orders/advance with no key returns 401 INVALID_API_KEY and changes no order.
 * 2. POST /internal/orders/advance with a wrong key returns 401 INVALID_API_KEY and changes no order.
 * 3. POST /internal/orders/advance with a valid user JWT but no API key returns 401 INVALID_API_KEY.
 * 4. POST /internal/orders/advance with the valid internal API key returns 200 and advances eligible orders.
 * 5. GET /api/orders with the internal API key and no JWT returns 401 AUTHENTICATION_REQUIRED (credentials not interchangeable).
 */
class InternalOrderAdvanceIT extends AbstractWebIT {

    private static final String ADVANCE_URL = "/internal/orders/advance";
    private static final String ORDERS_URL = "/api/orders";
    private static final String HEADER = "X-Internal-Api-Key";
    private static final String VALID_KEY = "test-internal-api-key";

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("POST /internal/orders/advance without API key returns 401 INVALID_API_KEY and leaves orders untouched")
    void advanceWithoutKeyReturns401() throws Exception {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);

        mockMvc.perform(post(ADVANCE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_API_KEY")));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("POST /internal/orders/advance with wrong API key returns 401 INVALID_API_KEY and leaves orders untouched")
    void advanceWithWrongKeyReturns401() throws Exception {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);

        mockMvc.perform(post(ADVANCE_URL).header(HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_API_KEY")));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("POST /internal/orders/advance with user JWT but no API key returns 401 INVALID_API_KEY")
    void advanceWithUserJwtReturns401() throws Exception {
        Caller customer = callers.registeredCustomer();
        Order order = testData.order(customer.userId(), OrderStatus.PENDING);

        mockMvc.perform(post(ADVANCE_URL).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_API_KEY")));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("POST /internal/orders/advance with valid API key returns 200 and advances eligible orders")
    void advanceWithCorrectKeyReturns200AndAdvances() throws Exception {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);

        mockMvc.perform(post(ADVANCE_URL).header(HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed", is(1)))
                .andExpect(jsonPath("$.shipped", is(0)))
                .andExpect(jsonPath("$.delivered", is(0)))
                .andExpect(jsonPath("$.failed", is(0)));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("API key header without JWT on user endpoints returns 401 AUTHENTICATION_REQUIRED (credentials not interchangeable)")
    void apiKeyOnUserEndpointRejected() throws Exception {
        mockMvc.perform(get(ORDERS_URL).header(HEADER, VALID_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));
    }
}
