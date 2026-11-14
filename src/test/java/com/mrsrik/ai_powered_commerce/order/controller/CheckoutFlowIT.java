package com.mrsrik.ai_powered_commerce.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.repository.CartItemRepository;
import com.mrsrik.ai_powered_commerce.cart.repository.CartRepository;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderItemRepository;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates the checkout-to-order flow (Requirements 4.1 to 4.9, 4.17, 11.3):
 * 1. Happy path checkout: creates PENDING order, snapshots items, decreases stock, empties cart, formats TXN- ID.
 * 2. Empty cart returns 409 CART_EMPTY.
 * 3. Insufficient stock returns 409 INSUFFICIENT_STOCK and rolls back earlier decrements and cart.
 * 4. Inactive or deleted product returns 409 PRODUCT_UNAVAILABLE and rolls back.
 * 5. Invalid shipping address returns 400 VALIDATION_FAILED identifying each invalid field.
 * 6. Order snapshots remain immutable after subsequent product edits or price changes.
 * 7. Unauthenticated request returns 401.
 */
class CheckoutFlowIT extends AbstractWebIT {

    private static final String ORDERS_URL = "/api/orders";

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String validAddressPayload() {
        return """
                {
                    "shippingAddress": {
                        "name": "Grace Hopper",
                        "phoneNumber": "+15551234567",
                        "addressLine1": "42 Naval Yard Way",
                        "addressLine2": "Suite 100",
                        "city": "Arlington",
                        "state": "VA",
                        "postalCode": "22201",
                        "country": "US"
                    }
                }
                """;
    }

    @Test
    @DisplayName("Unauthenticated checkout request returns 401 AUTHENTICATION_REQUIRED")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Happy path checkout: creates PENDING order with snapshot items, decrements stock, empties cart")
    void happyPathCheckout() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category(b -> b.name("Hardware"));

        Product p1 = testData.product(category.getId(), b -> b
                .name("Mechanical Keyboard")
                .brand("Keychron")
                .sku("KEY-CHRON-01")
                .sellingPrice(new BigDecimal("50.00"))
                .costPrice(new BigDecimal("30.00"))
                .stock(10));

        Product p2 = testData.product(category.getId(), b -> b
                .name("Optical Mouse")
                .brand("Logitech")
                .sku("LOGI-OPT-02")
                .sellingPrice(new BigDecimal("25.00"))
                .costPrice(new BigDecimal("15.00"))
                .stock(5));

        Cart cart = testData.cart(customer.userId());
        testData.cartItem(cart.getId(), p1.getId(), 2); // 2 * 50.00 = 100.00
        testData.cartItem(cart.getId(), p2.getId(), 1); // 1 * 25.00 = 25.00

        String response = perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalAmount").value(125.00))
                .andExpect(jsonPath("$.transactionId").value(org.hamcrest.Matchers.matchesPattern("^TXN-\\d{8}-[0-9A-F]{12}$")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.shippedAt").value(nullValue()))
                .andExpect(jsonPath("$.shippingAddress.name").value("Grace Hopper"))
                .andExpect(jsonPath("$.shippingAddress.phoneNumber").value("+15551234567"))
                .andExpect(jsonPath("$.shippingAddress.addressLine1").value("42 Naval Yard Way"))
                .andExpect(jsonPath("$.shippingAddress.addressLine2").value("Suite 100"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Arlington"))
                .andExpect(jsonPath("$.shippingAddress.state").value("VA"))
                .andExpect(jsonPath("$.shippingAddress.postalCode").value("22201"))
                .andExpect(jsonPath("$.shippingAddress.country").value("US"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode orderJson = objectMapper.readTree(response);
        UUID orderId = UUID.fromString(orderJson.get("id").asText());

        JsonNode itemsNode = orderJson.get("items");
        assertThat(itemsNode).hasSize(2);

        JsonNode keyboardItem = itemsNode.get(0).get("productName").asText().equals("Mechanical Keyboard")
                ? itemsNode.get(0) : itemsNode.get(1);
        JsonNode mouseItem = itemsNode.get(0).get("productName").asText().equals("Optical Mouse")
                ? itemsNode.get(0) : itemsNode.get(1);

        assertThat(keyboardItem.get("brand").asText()).isEqualTo("Keychron");
        assertThat(keyboardItem.get("sku").asText()).isEqualTo("KEY-CHRON-01");
        assertThat(keyboardItem.get("unitPrice").asDouble()).isEqualTo(50.00);
        assertThat(keyboardItem.get("quantity").asInt()).isEqualTo(2);
        assertThat(keyboardItem.get("subtotal").asDouble()).isEqualTo(100.00);

        assertThat(mouseItem.get("brand").asText()).isEqualTo("Logitech");
        assertThat(mouseItem.get("sku").asText()).isEqualTo("LOGI-OPT-02");
        assertThat(mouseItem.get("unitPrice").asDouble()).isEqualTo(25.00);
        assertThat(mouseItem.get("quantity").asInt()).isEqualTo(1);
        assertThat(mouseItem.get("subtotal").asDouble()).isEqualTo(25.00);

        // Verify stock decremented atomically
        Product reloadedP1 = productRepository.findById(p1.getId()).orElseThrow();
        assertThat(reloadedP1.getStock()).isEqualTo(8); // 10 - 2

        Product reloadedP2 = productRepository.findById(p2.getId()).orElseThrow();
        assertThat(reloadedP2.getStock()).isEqualTo(4); // 5 - 1

        // Verify cart items are hard-deleted, but cart still exists
        assertThat(cartItemRepository.findByCartId(cart.getId())).isEmpty();
        assertThat(cartRepository.findById(cart.getId())).isPresent();

        // Verify order saved in database
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getUserId()).isEqualTo(customer.userId());
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo("125.00");
        assertThat(savedOrder.getTotalItems()).isEqualTo(3);
    }

    @Test
    @DisplayName("Empty cart checkout returns 409 CART_EMPTY")
    void emptyCartReturns409() throws Exception {
        Caller customer = callers.registeredCustomer();

        // Customer has no cart row yet
        perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));

        // Customer has an empty cart row
        testData.cart(customer.userId());
        perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    @DisplayName("Insufficient stock on one item returns 409 INSUFFICIENT_STOCK and rolls back earlier decrements and cart")
    void insufficientStockRollsBackEntireCheckout() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        // p1 has stock 10, p2 has stock only 2
        Product p1 = testData.product(category.getId(), b -> b.stock(10).sellingPrice(new BigDecimal("10.00")));
        Product p2 = testData.product(category.getId(), b -> b.stock(2).sellingPrice(new BigDecimal("20.00")));

        Cart cart = testData.cart(customer.userId());
        testData.cartItem(cart.getId(), p1.getId(), 5);
        testData.cartItem(cart.getId(), p2.getId(), 4); // requested 4 > available 2

        perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        // Verify both product stocks are untouched
        Product reloadedP1 = productRepository.findById(p1.getId()).orElseThrow();
        assertThat(reloadedP1.getStock()).isEqualTo(10);

        Product reloadedP2 = productRepository.findById(p2.getId()).orElseThrow();
        assertThat(reloadedP2.getStock()).isEqualTo(2);

        // Verify cart items are unchanged
        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(2);

        // Verify no order was created
        assertThat(orderRepository.findByUserIdAndDeletedAtIsNull(customer.userId(), null).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Inactive or soft-deleted product at checkout returns 409 PRODUCT_UNAVAILABLE and rolls back")
    void inactiveOrDeletedProductReturns409ProductUnavailable() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        Product activeP = testData.product(category.getId(), b -> b.stock(10).active(true));
        Product inactiveP = testData.product(category.getId(), b -> b.stock(10).active(false));

        Cart cart = testData.cart(customer.userId());
        testData.cartItem(cart.getId(), activeP.getId(), 2);
        testData.cartItem(cart.getId(), inactiveP.getId(), 1);

        perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        // Verify active stock was rolled back
        Product reloadedActive = productRepository.findById(activeP.getId()).orElseThrow();
        assertThat(reloadedActive.getStock()).isEqualTo(10);

        // Verify cart items unchanged
        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Invalid shipping address returns 400 VALIDATION_FAILED identifying each invalid field")
    void invalidShippingAddressReturns400() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(10));

        Cart cart = testData.cart(customer.userId());
        testData.cartItem(cart.getId(), product.getId(), 1);

        String invalidPayload = """
                {
                    "shippingAddress": {
                        "name": "",
                        "phoneNumber": "+15551234567890123",
                        "addressLine1": "   ",
                        "city": "",
                        "state": "",
                        "postalCode": "1234567890123456789012",
                        "country": ""
                    }
                }
                """;

        perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload),
                customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));

        // Verify stock and cart are untouched
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(10);
        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Later price or product changes do not alter existing order snapshots")
    void laterProductChangesDoNotAlterExistingSnapshots() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        Product product = testData.product(category.getId(), b -> b
                .name("Original Name")
                .brand("Original Brand")
                .sku("ORIG-SKU")
                .sellingPrice(new BigDecimal("40.00"))
                .stock(10));

        Cart cart = testData.cart(customer.userId());
        testData.cartItem(cart.getId(), product.getId(), 1);

        String response = perform(
                post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAddressPayload()),
                customer)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        // Merchant later alters the product: price to 99.99, name changed, deactivated
        product.setName("Changed Name");
        product.setSellingPrice(new BigDecimal("99.99"));
        product.setActive(false);
        productRepository.saveAndFlush(product);

        // Verify order items snapshot is still original
        List<OrderItem> items = orderItemRepository.findByOrderIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(orderId);
        assertThat(items).hasSize(1);
        OrderItem item = items.getFirst();
        assertThat(item.getProductName()).isEqualTo("Original Name");
        assertThat(item.getBrand()).isEqualTo("Original Brand");
        assertThat(item.getSku()).isEqualTo("ORIG-SKU");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("40.00");

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("40.00");
    }
}

