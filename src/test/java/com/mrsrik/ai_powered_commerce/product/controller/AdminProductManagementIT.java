package com.mrsrik.ai_powered_commerce.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;
import com.mrsrik.ai_powered_commerce.support.TestCallers.Caller;

/**
 * Validates admin product management (Requirement 2):
 * create, update, delete, role gating (401/403), 404 for unknown/deleted products & categories,
 * 409 duplicate SKU (including soft-deleted), 400 validation for all invalid fields,
 * one-way deactivation and 409 reactivation rejection, and stray active ignored on create.
 */
class AdminProductManagementIT extends AbstractWebIT {

    @Autowired
    private ProductRepository productRepository;

    private static final String BASE_URL = "/api/products";

    private String validCreateJson(UUID categoryId, String sku) {
        return """
                {
                    "categoryId": "%s",
                    "name": "Mechanical Keyboard",
                    "description": "Clicky tactile mechanical switches",
                    "brand": "KeyMaster",
                    "sku": "%s",
                    "costPrice": 45.00,
                    "sellingPrice": 89.99,
                    "stock": 50
                }
                """.formatted(categoryId, sku);
    }

    @Test
    void adminCreatesProductSuccessfully() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();

        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(category.getId(), "KEY-001")))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.name", is("Mechanical Keyboard")))
                .andExpect(jsonPath("$.brand", is("KeyMaster")))
                .andExpect(jsonPath("$.sku", is("KEY-001")))
                .andExpect(jsonPath("$.costPrice", is(45.00)))
                .andExpect(jsonPath("$.sellingPrice", is(89.99)))
                .andExpect(jsonPath("$.stock", is(50)))
                .andExpect(jsonPath("$.images", empty()));

        Product saved = productRepository.findAll().stream()
                .filter(p -> "KEY-001".equals(p.getSku()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createIgnoresStrayActivePropertyInJsonBody() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();

        String jsonWithActiveFalse = """
                {
                    "categoryId": "%s",
                    "name": "Stray Active Product",
                    "description": "Desc",
                    "brand": "Brand",
                    "sku": "STRAY-ACTIVE-1",
                    "costPrice": 10.00,
                    "sellingPrice": 20.00,
                    "stock": 10,
                    "active": false
                }
                """.formatted(category.getId());

        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithActiveFalse))
                .andExpect(status().isCreated());

        Product saved = productRepository.findAll().stream()
                .filter(p -> "STRAY-ACTIVE-1".equals(p.getSku()))
                .findFirst()
                .orElseThrow();
        assertThat(saved.isActive()).isTrue(); // Stray active=false was ignored
    }

    @Test
    void adminUpdatesProductSuccessfully() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category c1 = testData.category(b -> b.name("Cat 1"));
        Category c2 = testData.category(b -> b.name("Cat 2"));
        Product product = testData.product(c1.getId(), b -> b.sku("ORIG-SKU"));
        testData.productImage(product.getId(), 1, b -> b.imageUrl("https://img.test/1.png"));

        String updateJson = """
                {
                    "categoryId": "%s",
                    "name": "Updated Name",
                    "description": "Updated Description",
                    "brand": "Updated Brand",
                    "sku": "ORIG-SKU",
                    "costPrice": 30.00,
                    "sellingPrice": 60.00,
                    "stock": 25,
                    "active": true
                }
                """.formatted(c2.getId());

        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId().toString())))
                .andExpect(jsonPath("$.categoryId", is(c2.getId().toString())))
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.costPrice", is(30.00)))
                .andExpect(jsonPath("$.sellingPrice", is(60.00)))
                .andExpect(jsonPath("$.stock", is(25)))
                .andExpect(jsonPath("$.images", hasSize(1)));

        Product refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Updated Name");
        assertThat(refreshed.getCategoryId()).isEqualTo(c2.getId());
    }

    @Test
    void oneWayDeactivationAndReactivationRejection() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b.sku("DEACT-SKU").active(true));

        // 1. Deactivate product: active = false
        String deactivateJson = """
                {
                    "categoryId": "%s",
                    "name": "%s",
                    "description": "%s",
                    "brand": "%s",
                    "sku": "%s",
                    "costPrice": 10.00,
                    "sellingPrice": 20.00,
                    "stock": 5,
                    "active": false
                }
                """.formatted(category.getId(), product.getName(), product.getDescription(), product.getBrand(), product.getSku());

        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(deactivateJson))
                .andExpect(status().isOk());

        Product deactivated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();

        // Deactivated product disappears from public catalog browse
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()));

        // 2. Editing inactive product without changing active (active = null) succeeds
        String editInactiveJson = """
                {
                    "categoryId": "%s",
                    "name": "Edited While Inactive",
                    "description": "%s",
                    "brand": "%s",
                    "sku": "%s",
                    "costPrice": 12.00,
                    "sellingPrice": 22.00,
                    "stock": 5
                }
                """.formatted(category.getId(), product.getDescription(), product.getBrand(), product.getSku());

        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(editInactiveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Edited While Inactive")));

        // 3. Reactivation attempt (active = true) is rejected with 409 PRODUCT_REACTIVATION_NOT_SUPPORTED
        String reactivateJson = """
                {
                    "categoryId": "%s",
                    "name": "Reactivate Attempt",
                    "description": "%s",
                    "brand": "%s",
                    "sku": "%s",
                    "costPrice": 10.00,
                    "sellingPrice": 20.00,
                    "stock": 5,
                    "active": true
                }
                """.formatted(category.getId(), product.getDescription(), product.getBrand(), product.getSku());

        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reactivateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_REACTIVATION_NOT_SUPPORTED")));

        // Entity name remains unchanged by the rejected request
        Product stillInactive = productRepository.findById(product.getId()).orElseThrow();
        assertThat(stillInactive.isActive()).isFalse();
        assertThat(stillInactive.getName()).isEqualTo("Edited While Inactive");
    }

    @Test
    void adminDeletesProductSoftDeleteAndRepeatIs404() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();
        Product product = testData.product(category.getId());

        // First delete -> 204 No Content
        mockMvc.perform(delete(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNoContent());

        Product deleted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();

        // Repeat delete -> 404 PRODUCT_NOT_FOUND
        mockMvc.perform(delete(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));

        // Update on deleted product -> 404 PRODUCT_NOT_FOUND
        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(category.getId(), "SOME-SKU")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void roleGatingReturns401Or403ForNonAdmin() throws Exception {
        Caller customer = callers.registeredCustomer();
        Caller unregistered = callers.unregistered();
        Category category = testData.category();
        Product product = testData.product(category.getId());
        String body = validCreateJson(category.getId(), "SKU-AUTH");

        // 1. Anonymous -> 401
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(put(BASE_URL + "/" + product.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        mockMvc.perform(delete(BASE_URL + "/" + product.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        // 2. Customer -> 403 FORBIDDEN
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        mockMvc.perform(put(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        mockMvc.perform(delete(BASE_URL + "/" + product.getId())
                .header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN")));

        // 3. Unregistered -> 403 USER_NOT_REGISTERED
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, unregistered.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("USER_NOT_REGISTERED")));
    }

    @Test
    void duplicateSkuReturns409IncludingSoftDeletedSku() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category category = testData.category();
        testData.product(category.getId(), b -> b.sku("ACTIVE-SKU"));
        testData.product(category.getId(), b -> b.sku("DELETED-SKU").deletedAt(Instant.now()));

        // Create with duplicate active SKU -> 409
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(category.getId(), "ACTIVE-SKU")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_SKU")));

        // Create with duplicate deleted SKU -> 409 (SKUs never reused)
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(category.getId(), "DELETED-SKU")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_SKU")));

        // Update with another product's SKU -> 409
        Product p3 = testData.product(category.getId(), b -> b.sku("P3-SKU"));
        mockMvc.perform(put(BASE_URL + "/" + p3.getId())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(category.getId(), "ACTIVE-SKU")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_SKU")));
    }

    @Test
    void missingOrSoftDeletedCategoryReturns404() throws Exception {
        Caller admin = callers.registeredAdmin();
        Category deletedCategory = testData.category(b -> b.deletedAt(Instant.now()));
        UUID unknownCategoryId = UUID.randomUUID();

        // Create with unknown category -> 404
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(unknownCategoryId, "SKU-CAT-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));

        // Create with soft-deleted category -> 404
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateJson(deletedCategory.getId(), "SKU-CAT-2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void invalidFieldValidationReportsEveryInvalidField() throws Exception {
        Caller admin = callers.registeredAdmin();

        // Request with every field invalid: blank/oversized strings, negative prices, negative stock
        String invalidJson = """
                {
                    "name": "",
                    "description": "",
                    "brand": "%s",
                    "sku": "",
                    "costPrice": -5.00,
                    "sellingPrice": -10.00,
                    "stock": -1
                }
                """.formatted("B".repeat(101));

        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        Caller admin = callers.registeredAdmin();
        mockMvc.perform(post(BASE_URL)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }
}
