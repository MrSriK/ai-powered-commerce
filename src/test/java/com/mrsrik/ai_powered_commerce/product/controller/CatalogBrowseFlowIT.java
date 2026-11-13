package com.mrsrik.ai_powered_commerce.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;
import com.mrsrik.ai_powered_commerce.support.AbstractWebIT;

/**
 * Validates the public catalog browse flow (Requirement 1, Requirement 5.8, Requirement 8.5, Requirement 11.3):
 * public access without tokens, filtering, search, pagination bounds, soft-delete hiding,
 * product detail with ordered images, pricing visibility with two decimals, and error codes.
 */
class CatalogBrowseFlowIT extends AbstractWebIT {

    @Test
    void emptyCatalogReturns200WithZeroTotal() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    void publicAccessWithoutTokenReturnsProductsWithCostAndSellingPriceTwoDecimals() throws Exception {
        Category category = testData.category(b -> b.name("Audio"));
        Product product = testData.product(category.getId(), b -> b
                .name("Wireless Earbuds")
                .brand("SoundCo")
                .sku("EARBUD-001")
                .costPrice(new BigDecimal("25.50"))
                .sellingPrice(new BigDecimal("59.99"))
                .stock(40));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(product.getId().toString())))
                .andExpect(jsonPath("$.content[0].categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.content[0].name", is("Wireless Earbuds")))
                .andExpect(jsonPath("$.content[0].brand", is("SoundCo")))
                .andExpect(jsonPath("$.content[0].sku", is("EARBUD-001")))
                .andExpect(jsonPath("$.content[0].costPrice", is(25.50)))
                .andExpect(jsonPath("$.content[0].sellingPrice", is(59.99)))
                .andExpect(jsonPath("$.content[0].stock", is(40)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)));
    }

    @Test
    void filtersByCategory() throws Exception {
        Category cat1 = testData.category(b -> b.name("Laptops"));
        Category cat2 = testData.category(b -> b.name("Keyboards"));

        Product p1 = testData.product(cat1.getId(), b -> b.name("Pro Laptop"));
        testData.product(cat2.getId(), b -> b.name("Mechanical Keyboard"));

        mockMvc.perform(get("/api/products").param("categoryId", cat1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(p1.getId().toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchFiltersCaseInsensitiveAndIgnoresBlankTerm() throws Exception {
        Category category = testData.category();
        Product p1 = testData.product(category.getId(), b -> b.name("Gamer Mouse").description("RGB optical mouse"));
        Product p2 = testData.product(category.getId(), b -> b.name("USB Cable").description("High speed gamer cord"));
        Product p3 = testData.product(category.getId(), b -> b.name("Monitor Stand").description("Aluminum riser"));

        // Match in name
        mockMvc.perform(get("/api/products").param("search", "mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(p1.getId().toString())));

        // Match in description, case-insensitive
        mockMvc.perform(get("/api/products").param("search", "GAMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is(p1.getId().toString())))
                .andExpect(jsonPath("$.content[1].id", is(p2.getId().toString())));

        // Blank search term is ignored
        mockMvc.perform(get("/api/products").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    void searchTermOver255CharactersReturns400ValidationFailed() throws Exception {
        String longSearch = "a".repeat(256);

        mockMvc.perform(get("/api/products").param("search", longSearch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void paginationAppliesDefaultsAndEnforcesBoundsWith400() throws Exception {
        Category category = testData.category();
        for (int i = 1; i <= 25; i++) {
            final int index = i;
            testData.product(category.getId(), b -> b.name(String.format("Product %02d", index)));
        }

        // Default: page 0, size 20 -> 20 items returned, 2 total pages
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(25)))
                .andExpect(jsonPath("$.totalPages", is(2)));

        // Page 1, size 10 -> 10 items returned
        mockMvc.perform(get("/api/products").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(10)));

        // Invalid pagination: size < 1
        mockMvc.perform(get("/api/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));

        // Invalid pagination: size > 100
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));

        // Invalid pagination: page < 0
        mockMvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAGINATION")));
    }

    @Test
    void excludesInactiveAndSoftDeletedProductsAndCategories() throws Exception {
        Category activeCat = testData.category(b -> b.name("Active Cat"));
        Category deletedCat = testData.category(b -> b.name("Deleted Cat").deletedAt(Instant.now()));

        Product activeProd = testData.product(activeCat.getId(), b -> b.name("Active Prod").active(true));
        testData.product(activeCat.getId(), b -> b.name("Inactive Prod").active(false));
        testData.product(activeCat.getId(), b -> b.name("Deleted Prod").deletedAt(Instant.now()));
        testData.product(deletedCat.getId(), b -> b.name("Prod in Deleted Cat").active(false));

        // Products list only returns active, non-deleted product
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(activeProd.getId().toString())));

        // Categories list only returns non-deleted category
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(activeCat.getId().toString())))
                .andExpect(jsonPath("$[0].name", is("Active Cat")));
    }

    @Test
    void productDetailWithOrderedImages() throws Exception {
        Category category = testData.category();
        Product product = testData.product(category.getId(), b -> b
                .name("Tablet Pro")
                .description("10 inch high resolution display")
                .brand("TabBrand")
                .sku("TAB-PRO-10")
                .costPrice(new BigDecimal("199.00"))
                .sellingPrice(new BigDecimal("349.99"))
                .stock(15));

        ProductImage img2 = testData.productImage(product.getId(), 2, b -> b.imageUrl("https://img.test/2.png"));
        ProductImage img1 = testData.productImage(product.getId(), 1, b -> b.imageUrl("https://img.test/1.png"));
        testData.productImage(product.getId(), 3, b -> b.deletedAt(Instant.now()));

        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId().toString())))
                .andExpect(jsonPath("$.categoryId", is(category.getId().toString())))
                .andExpect(jsonPath("$.name", is("Tablet Pro")))
                .andExpect(jsonPath("$.description", is("10 inch high resolution display")))
                .andExpect(jsonPath("$.brand", is("TabBrand")))
                .andExpect(jsonPath("$.sku", is("TAB-PRO-10")))
                .andExpect(jsonPath("$.costPrice", is(199.00)))
                .andExpect(jsonPath("$.sellingPrice", is(349.99)))
                .andExpect(jsonPath("$.stock", is(15)))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id", is(img1.getId().toString())))
                .andExpect(jsonPath("$.images[0].displayOrder", is(1)))
                .andExpect(jsonPath("$.images[0].imageUrl", is("https://img.test/1.png")))
                .andExpect(jsonPath("$.images[1].id", is(img2.getId().toString())))
                .andExpect(jsonPath("$.images[1].displayOrder", is(2)))
                .andExpect(jsonPath("$.images[1].imageUrl", is("https://img.test/2.png")));
    }

    @Test
    void productDetailNotFoundForMissingInactiveOrDeleted() throws Exception {
        Category category = testData.category();
        Product inactive = testData.product(category.getId(), b -> b.active(false));
        Product deleted = testData.product(category.getId(), b -> b.deletedAt(Instant.now()));

        mockMvc.perform(get("/api/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));

        mockMvc.perform(get("/api/products/" + inactive.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));

        mockMvc.perform(get("/api/products/" + deleted.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void malformedUuidReturns400InvalidIdentifier() throws Exception {
        mockMvc.perform(get("/api/products/not-a-valid-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_IDENTIFIER")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message").exists());
    }
}

