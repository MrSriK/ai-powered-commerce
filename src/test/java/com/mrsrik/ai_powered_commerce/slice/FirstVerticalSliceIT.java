package com.mrsrik.ai_powered_commerce.slice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * End-to-end first vertical slice integration test (Task 23):
 * 1. An unregistered caller with a valid signed token registers via POST /api/users/register (201).
 * 2. The newly registered user queries their profile via GET /api/users/me (200).
 * 3. The user browses the public catalog via GET /api/products (200).
 * 4. The customer adds browsed products to their cart via POST /api/cart/items (200).
 * 5. The customer views their cart via GET /api/cart, verifying line items, quantities,
 *    unit prices, subtotals, item counts, and total amount (200).
 *
 * Requirements: 1.1, 3.2, 3.3, 13.1, 13.10, 11.3
 */
class FirstVerticalSliceIT extends AbstractWebIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("End-to-end vertical slice: register -> get profile -> browse catalog -> add to cart -> view cart")
    void endToEndCustomerJourney() throws Exception {
        // Seed catalog with a category and active products
        Category electronics = testData.category(b -> b.name("Electronics"));
        Product keyboard = testData.product(electronics.getId(), b -> b
                .name("Mechanical Keyboard")
                .brand("Keychron")
                .sku("KEY-001")
                .costPrice(new BigDecimal("45.00"))
                .sellingPrice(new BigDecimal("79.99"))
                .stock(50));
        Product mouse = testData.product(electronics.getId(), b -> b
                .name("Wireless Mouse")
                .brand("Logitech")
                .sku("LOG-002")
                .costPrice(new BigDecimal("20.00"))
                .sellingPrice(new BigDecimal("39.99"))
                .stock(25));

        // 1. Unregistered identity registers via POST /api/users/register
        Caller customer = callers.unregistered(b -> b.email("ada.lovelace@example.test").emailVerified(true));

        String registerPayload = """
                {
                    "firstName": "Ada",
                    "lastName": "Lovelace",
                    "email": "ada.lovelace@example.test",
                    "phoneNumber": "+15551234567"
                }
                """;

        String registerResponse = perform(
                post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload),
                customer)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.email").value("ada.lovelace@example.test"))
                .andExpect(jsonPath("$.phoneNumber").value("+15551234567"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode userJson = objectMapper.readTree(registerResponse);
        UUID registeredUserId = UUID.fromString(userJson.get("id").asText());
        assertThat(registeredUserId).isNotNull();

        // 2. Query registered user profile via GET /api/users/me
        perform(get("/api/users/me"), customer)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(registeredUserId.toString()))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"))
                .andExpect(jsonPath("$.email").value("ada.lovelace@example.test"))
                .andExpect(jsonPath("$.phoneNumber").value("+15551234567"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        // 3. Browse public catalog via GET /api/products
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(keyboard.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.content[0].costPrice").value(45.00))
                .andExpect(jsonPath("$.content[0].sellingPrice").value(79.99))
                .andExpect(jsonPath("$.content[1].id").value(mouse.getId().toString()))
                .andExpect(jsonPath("$.content[1].name").value("Wireless Mouse"))
                .andExpect(jsonPath("$.content[1].costPrice").value(20.00))
                .andExpect(jsonPath("$.content[1].sellingPrice").value(39.99));

        // 4. Add items to cart via POST /api/cart/items
        String addKeyboardPayload = """
                {
                    "productId": "%s",
                    "quantity": 2
                }
                """.formatted(keyboard.getId());

        perform(
                post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addKeyboardPayload),
                customer)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalAmount").value(159.98));

        String addMousePayload = """
                {
                    "productId": "%s",
                    "quantity": 1
                }
                """.formatted(mouse.getId());

        perform(
                post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMousePayload),
                customer)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalAmount").value(199.97));

        // 5. View cart via GET /api/cart
        perform(get("/api/cart"), customer)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cartId").isNotEmpty())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalAmount").value(199.97))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId").value(keyboard.getId().toString()))
                .andExpect(jsonPath("$.items[0].productName").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.items[0].brand").value("Keychron"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(79.99))
                .andExpect(jsonPath("$.items[0].subtotal").value(159.98))
                .andExpect(jsonPath("$.items[1].productId").value(mouse.getId().toString()))
                .andExpect(jsonPath("$.items[1].productName").value("Wireless Mouse"))
                .andExpect(jsonPath("$.items[1].brand").value("Logitech"))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.items[1].unitPrice").value(39.99))
                .andExpect(jsonPath("$.items[1].subtotal").value(39.99));
    }
}
