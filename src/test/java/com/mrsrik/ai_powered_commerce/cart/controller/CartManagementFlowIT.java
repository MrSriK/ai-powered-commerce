package com.mrsrik.ai_powered_commerce.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates the shopping cart flow (Requirement 3):
 * 401 unauthenticated, get-or-create empty cart, add items, merge and 999 cap,
 * stock limit enforcement (409), inactive/deleted product handling (404),
 * validation of 1-999 bounds (400), physical delete on remove (200),
 * scoping and isolation between users (404), and removing lines for inactive/deleted products.
 */
class CartManagementFlowIT extends AbstractWebIT {

    private static final String BASE_URL = "/api/cart";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String addJson(UUID productId, int quantity) {
        return """
                {
                    "productId": "%s",
                    "quantity": %d
                }
                """.formatted(productId, quantity);
    }

    private String updateJson(int quantity) {
        return """
                {
                    "quantity": %d
                }
                """.formatted(quantity);
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(UUID.randomUUID(), 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(put(BASE_URL + "/items/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(delete(BASE_URL + "/items/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));
    }

    @Test
    void firstGetCreatesEmptyCartAndSubsequentGetIsIdempotent() throws Exception {
        Caller customer = callers.registeredCustomer();

        String response1 = perform(get(BASE_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").isString())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems", is(0)))
                .andExpect(jsonPath("$.totalAmount", is(0.00)))
                .andReturn().getResponse().getContentAsString();

        String cartId1 = objectMapper.readTree(response1).get("cartId").asText();

        String response2 = perform(get(BASE_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems", is(0)))
                .andReturn().getResponse().getContentAsString();

        String cartId2 = objectMapper.readTree(response2).get("cartId").asText();
        assertThat(cartId1).isEqualTo(cartId2);

        // Verify database row
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE id = ?::uuid AND user_id = ?::uuid",
                Integer.class, cartId1, customer.userId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void addItemCreatesNewLineWithCalculatedSubtotalAndTotals() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .name("Wireless Mouse")
                .brand("LogiTech")
                .sellingPrice(new BigDecimal("25.50"))
                .stock(20));

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId", is(product.getId().toString())))
                .andExpect(jsonPath("$.items[0].productName", is("Wireless Mouse")))
                .andExpect(jsonPath("$.items[0].brand", is("LogiTech")))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].unitPrice", is(25.50)))
                .andExpect(jsonPath("$.items[0].subtotal", is(51.00)))
                .andExpect(jsonPath("$.totalItems", is(2)))
                .andExpect(jsonPath("$.totalAmount", is(51.00)));
    }

    @Test
    void addItemMergesExistingLineAndCapsAt999() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .sellingPrice(new BigDecimal("10.00"))
                .stock(1000));

        // Add 500
        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 500)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(500)))
                .andExpect(jsonPath("$.totalItems", is(500)));

        // Add 500 again -> caps at 999
        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 500)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(999)))
                .andExpect(jsonPath("$.totalItems", is(999)))
                .andExpect(jsonPath("$.totalAmount", is(9990.00)));
    }

    @Test
    void addItemExceedingStockReturns409AndLeavesCartUnchanged() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .sellingPrice(new BigDecimal("15.00"))
                .stock(5));

        // Add 3 -> succeeds
        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 3)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity", is(3)));

        // Add 3 again -> would be 6, exceeds stock 5 -> 409
        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 3)), customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_STOCK")));

        // Verify cart is unchanged (quantity is still 3)
        perform(get(BASE_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(3)))
                .andExpect(jsonPath("$.totalItems", is(3)));
    }

    @Test
    void addItemWithInactiveOrDeletedOrUnknownProductReturns404() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();

        Product inactiveProduct = testData.product(category.getId(), b -> b.active(false));
        Product deletedProduct = testData.product(category.getId(), b -> b.deletedAt(Instant.now()));
        UUID unknownId = UUID.randomUUID();

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(inactiveProduct.getId(), 1)), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_UNAVAILABLE")));

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(deletedProduct.getId(), 1)), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_UNAVAILABLE")));

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(unknownId, 1)), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_UNAVAILABLE")));

        // Cart remains empty
        perform(get(BASE_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void addItemValidationRejectsQuantityLessThanOneOrGreaterThan999() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 0)), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 1000)), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 1}"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    void updateItemUpdatesQuantityAndTotals() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .sellingPrice(new BigDecimal("20.00"))
                .stock(10));

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Update quantity to 4
        perform(put(BASE_URL + "/items/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(4)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(4)))
                .andExpect(jsonPath("$.items[0].subtotal", is(80.00)))
                .andExpect(jsonPath("$.totalItems", is(4)))
                .andExpect(jsonPath("$.totalAmount", is(80.00)));
    }

    @Test
    void updateItemExceedingStockReturns409AndLeavesCartUnchanged() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .sellingPrice(new BigDecimal("10.00"))
                .stock(5));

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 3)), customer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Update to 6 > stock 5 -> 409
        perform(put(BASE_URL + "/items/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(6)), customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_STOCK")));

        // Quantity remains 3
        perform(get(BASE_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity", is(3)));
    }

    @Test
    void updateItemWhenProductBecameInactiveOrDeletedReturns404AndLeavesCartUnchanged() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.stock(10));

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Product becomes inactive
        jdbcTemplate.update("UPDATE products SET is_active = false WHERE id = ?::uuid", product.getId());

        // Update returns 404 PRODUCT_UNAVAILABLE
        perform(put(BASE_URL + "/items/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(5)), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_UNAVAILABLE")));

        // Verify cart quantity is still 2 in database
        Integer qty = jdbcTemplate.queryForObject(
                "SELECT quantity FROM cart_items WHERE id = ?::uuid", Integer.class, itemId);
        assertThat(qty).isEqualTo(2);
    }

    @Test
    void updateItemValidationRejectsQuantityOutOfRange() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        perform(put(BASE_URL + "/items/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(0)), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        perform(put(BASE_URL + "/items/" + itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(1000)), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    @Test
    void updateItemUnknownOrOtherUsersItemReturns404() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customerA)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemAId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Customer B tries to update Customer A's item -> 404 CART_ITEM_NOT_FOUND
        perform(put(BASE_URL + "/items/" + itemAId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(5)), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")));

        // Random UUID -> 404
        perform(put(BASE_URL + "/items/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(5)), customerA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")));
    }

    @Test
    void removeItemDeletesRowPhysicallyAndRecalculatesTotals() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product p1 = testData.product(category.getId(), b -> b.sellingPrice(new BigDecimal("10.00")));
        Product p2 = testData.product(category.getId(), b -> b.sellingPrice(new BigDecimal("20.00")));

        perform(post(BASE_URL + "/items").contentType(MediaType.APPLICATION_JSON).content(addJson(p1.getId(), 1)), customer);
        String add2Response = perform(post(BASE_URL + "/items").contentType(MediaType.APPLICATION_JSON).content(addJson(p2.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(add2Response);
        String item1Id = root.get("items").get(0).get("itemId").asText();
        String item2Id = root.get("items").get(1).get("itemId").asText();

        // Delete item 1
        perform(delete(BASE_URL + "/items/" + item1Id), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].itemId", is(item2Id)))
                .andExpect(jsonPath("$.totalItems", is(2)))
                .andExpect(jsonPath("$.totalAmount", is(40.00)));

        // Verify row is physically deleted from database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cart_items WHERE id = ?::uuid", Integer.class, item1Id);
        assertThat(count).isZero();
    }

    @Test
    void removeItemWhenProductBecameInactiveOrDeletedSucceeds() throws Exception {
        Caller customer = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customer)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Product soft-deleted in database
        jdbcTemplate.update("UPDATE products SET deleted_at = NOW() WHERE id = ?::uuid", product.getId());

        // Remove item succeeds (Requirement 3.12)
        perform(delete(BASE_URL + "/items/" + itemId), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems", is(0)))
                .andExpect(jsonPath("$.totalAmount", is(0.00)));

        // Verify physically deleted
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cart_items WHERE id = ?::uuid", Integer.class, itemId);
        assertThat(count).isZero();
    }

    @Test
    void removeItemUnknownOrOtherUsersItemReturns404() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        String addResponse = perform(post(BASE_URL + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addJson(product.getId(), 2)), customerA)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String itemAId = objectMapper.readTree(addResponse).get("items").get(0).get("itemId").asText();

        // Customer B tries to delete Customer A's item -> 404 CART_ITEM_NOT_FOUND
        perform(delete(BASE_URL + "/items/" + itemAId), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")));

        // Item still in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cart_items WHERE id = ?::uuid", Integer.class, itemAId);
        assertThat(count).isEqualTo(1);

        // Random UUID -> 404
        perform(delete(BASE_URL + "/items/" + UUID.randomUUID()), customerA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")));
    }
}
