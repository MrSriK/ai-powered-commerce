package com.mrsrik.ai_powered_commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;
import com.mrsrik.ai_powered_commerce.cart.repository.CartItemRepository;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates owner-scoped resource authorization (Requirements 3.8, 3.10, 4.10, 4.11, 4.13, 6.4, 11.3):
 * - User B reading User A's order returns 404 ORDER_NOT_FOUND (identical to non-existent id).
 * - User B listing orders only sees User B's orders; User A's orders are never exposed.
 * - Order list with invalid pagination returns 400 INVALID_PAGINATION.
 * - User B updating User A's cart item returns 404 CART_ITEM_NOT_FOUND without modifying data.
 * - User B deleting User A's cart item returns 404 CART_ITEM_NOT_FOUND without modifying data.
 * - Soft-deleted orders are excluded from order list and return 404 on direct get.
 * - Owner (User A) can view their own order details with all items.
 * (AI conversation owner scoping is extended in Task 36.3).
 */
class OwnerScopedAuthorizationIT extends AbstractWebIT {

    private static final String ORDERS_URL = "/api/orders";
    private static final String CART_ITEMS_URL = "/api/cart/items";
    private static final String AI_CONVERSATIONS_URL = "/api/ai/conversations";

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.mrsrik.ai_powered_commerce.ai.repository.AiConversationRepository aiConversationRepository;

    @Test
    @DisplayName("User B cannot read User A's order and receives identical 404 as non-existent id")
    void customerCannotReadAnotherUsersOrder() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId());
        Order orderA = testData.order(customerA.userId(), OrderStatus.PENDING);
        testData.orderItem(orderA.getId(), product.getId(), 2);

        // User B attempting to read User A's order
        perform(get(ORDERS_URL + "/" + orderA.getId()), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Order not found")));

        // User B requesting a completely non-existent order id
        UUID nonExistentOrderId = UUID.randomUUID();
        perform(get(ORDERS_URL + "/" + nonExistentOrderId), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Order not found")));
    }

    @Test
    @DisplayName("Order listing is strictly owner-scoped and ordered createdAt DESC, id DESC")
    void orderListOnlyReturnsCallingUsersOrders() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId());

        Order orderA1 = testData.order(customerA.userId(), OrderStatus.PENDING);
        Order orderA2 = testData.order(customerA.userId(), OrderStatus.CONFIRMED);
        Order orderB1 = testData.order(customerB.userId(), OrderStatus.PENDING);

        // User B listing orders: only sees B's order
        perform(get(ORDERS_URL), customerB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(orderB1.getId().toString())));

        // User A listing orders: sees both A orders, ordered createdAt DESC, id DESC
        perform(get(ORDERS_URL), customerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is(orderA2.getId().toString())))
                .andExpect(jsonPath("$.content[1].id", is(orderA1.getId().toString())));
    }

    @Test
    @DisplayName("Order listing with invalid pagination parameters returns 400 INVALID_PAGINATION")
    void orderListRejectsInvalidPagination() throws Exception {
        Caller customer = callers.registeredCustomer();

        perform(get(ORDERS_URL).param("page", "-1"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));

        perform(get(ORDERS_URL).param("size", "0"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));

        perform(get(ORDERS_URL).param("size", "101"), customer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));
    }

    @Test
    @DisplayName("User B cannot update User A's cart item and receives identical 404 as non-existent id")
    void customerCannotUpdateAnotherUsersCartItem() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId());
        Cart cartA = testData.cart(customerA.userId());
        CartItem itemA = testData.cartItem(cartA.getId(), product.getId(), 2);

        String updateBody = "{\"quantity\": 5}";

        // User B attempting to update User A's cart item
        perform(put(CART_ITEMS_URL + "/" + itemA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Cart item not found")));

        // Verify User A's cart item quantity is completely unchanged
        CartItem reloaded = cartItemRepository.findById(itemA.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(2);

        // User B updating a non-existent item id gets identical 404 and code
        UUID nonExistentItemId = UUID.randomUUID();
        perform(put(CART_ITEMS_URL + "/" + nonExistentItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Cart item not found")));
    }

    @Test
    @DisplayName("User B cannot delete User A's cart item and receives identical 404 as non-existent id")
    void customerCannotDeleteAnotherUsersCartItem() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId());
        Cart cartA = testData.cart(customerA.userId());
        CartItem itemA = testData.cartItem(cartA.getId(), product.getId(), 2);

        // User B attempting to delete User A's cart item
        perform(delete(CART_ITEMS_URL + "/" + itemA.getId()), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Cart item not found")));

        // Verify User A's cart item still exists
        assertThat(cartItemRepository.findById(itemA.getId())).isPresent();

        // User B deleting a non-existent item id gets identical 404 and code
        UUID nonExistentItemId = UUID.randomUUID();
        perform(delete(CART_ITEMS_URL + "/" + nonExistentItemId), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CART_ITEM_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Cart item not found")));
    }

    @Test
    @DisplayName("Owner can read own order with full details and order items")
    void ownerCanReadOwnOrderWithFullDetails() throws Exception {
        Caller customer = callers.registeredCustomer();

        Category category = testData.category();
        Product product = testData.product(category.getId());
        Order order = testData.order(customer.userId(), OrderStatus.PENDING);
        OrderItem item = testData.orderItem(order.getId(), product.getId(), 3);

        perform(get(ORDERS_URL + "/" + order.getId()), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(order.getId().toString())))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalItems", is(order.getTotalItems())))
                .andExpect(jsonPath("$.totalAmount", is(order.getTotalAmount().doubleValue())))
                .andExpect(jsonPath("$.transactionId", is(order.getTransactionId())))
                .andExpect(jsonPath("$.shippingAddress.name", is(order.getShippingName())))
                .andExpect(jsonPath("$.shippingAddress.phoneNumber", is(order.getShippingPhoneNumber())))
                .andExpect(jsonPath("$.shippingAddress.addressLine1", is(order.getShippingAddressLine1())))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId", is(product.getId().toString())))
                .andExpect(jsonPath("$.items[0].productName", is(item.getProductName())))
                .andExpect(jsonPath("$.items[0].quantity", is(3)));
    }

    @Test
    @DisplayName("Soft-deleted order returns 404 on direct get and is excluded from list")
    void softDeletedOrderIsExcludedFromListAndDirectLookup() throws Exception {
        Caller customer = callers.registeredCustomer();

        Order activeOrder = testData.order(customer.userId(), OrderStatus.PENDING);
        Order deletedOrder = testData.order(customer.userId(), OrderStatus.CONFIRMED, b -> b.deletedAt(Instant.now()));

        // Direct get of soft-deleted order returns 404 ORDER_NOT_FOUND
        perform(get(ORDERS_URL + "/" + deletedOrder.getId()), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")));

        // Direct get of active order succeeds
        perform(get(ORDERS_URL + "/" + activeOrder.getId()), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(activeOrder.getId().toString())));

        // Order list only contains active order
        perform(get(ORDERS_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(activeOrder.getId().toString())));
    }

    @Test
    @DisplayName("User B cannot access User A's AI conversation and receives 404 CONVERSATION_NOT_FOUND")
    void customerCannotAccessAnotherUsersConversation() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        var convA = aiConversationRepository.saveAndFlush(
                com.mrsrik.ai_powered_commerce.ai.entity.AiConversation.builder()
                        .userId(customerA.userId())
                        .title("Private Conversation")
                        .build());

        // User B cannot read User A's conversation history -> 404
        perform(get(AI_CONVERSATIONS_URL + "/" + convA.getId() + "/messages"), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CONVERSATION_NOT_FOUND")));

        // User B cannot post to User A's conversation -> 404
        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(AI_CONVERSATIONS_URL + "/" + convA.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Intrusion attempt\"}"), customerB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CONVERSATION_NOT_FOUND")));
    }

    @Test
    @DisplayName("Customer only sees their own AI conversations in conversation list")
    void customerOnlySeesOwnConversationsInList() throws Exception {
        Caller customerA = callers.registeredCustomer();
        Caller customerB = callers.registeredCustomer();

        var convA = aiConversationRepository.saveAndFlush(
                com.mrsrik.ai_powered_commerce.ai.entity.AiConversation.builder()
                        .userId(customerA.userId())
                        .title("User A Chat")
                        .build());

        var convB = aiConversationRepository.saveAndFlush(
                com.mrsrik.ai_powered_commerce.ai.entity.AiConversation.builder()
                        .userId(customerB.userId())
                        .title("User B Chat")
                        .build());

        perform(get(AI_CONVERSATIONS_URL), customerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(convA.getId().toString())));

        perform(get(AI_CONVERSATIONS_URL), customerB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(convB.getId().toString())));
    }

    @Test
    @DisplayName("Soft-deleted AI conversation returns 404 and is excluded from list")
    void softDeletedConversationIsExcludedAndReturns404() throws Exception {
        Caller customer = callers.registeredCustomer();

        var activeConv = aiConversationRepository.saveAndFlush(
                com.mrsrik.ai_powered_commerce.ai.entity.AiConversation.builder()
                        .userId(customer.userId())
                        .title("Active Chat")
                        .build());

        var deletedConv = aiConversationRepository.saveAndFlush(
                com.mrsrik.ai_powered_commerce.ai.entity.AiConversation.builder()
                        .userId(customer.userId())
                        .title("Deleted Chat")
                        .deletedAt(Instant.now())
                        .build());

        // Direct get messages on soft-deleted conversation -> 404
        perform(get(AI_CONVERSATIONS_URL + "/" + deletedConv.getId() + "/messages"), customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CONVERSATION_NOT_FOUND")));

        // List only contains active conversation
        perform(get(AI_CONVERSATIONS_URL), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(activeConv.getId().toString())));
    }
}
