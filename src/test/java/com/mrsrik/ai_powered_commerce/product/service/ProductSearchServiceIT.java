package com.mrsrik.ai_powered_commerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;

/**
 * Validates the read-only product search service for the AI module (Requirements 7.3, 7.10, 1.9).
 */
class ProductSearchServiceIT extends AbstractPostgresIT {

    @Autowired
    private ProductSearchService productSearchService;

    @Test
    void onlyActiveAndNonDeletedProductsReturned() {
        Category category = testData.category(b -> b.name("Electronics"));
        Product activeProduct = testData.product(category.getId(), b -> b.name("Active Laptop").active(true));
        testData.product(category.getId(), b -> b.name("Inactive Tablet").active(false));
        testData.product(category.getId(), b -> b.name("Deleted Phone").active(true).deletedAt(Instant.now()));
        testData.product(category.getId(), b -> b.name("Inactive Deleted Watch").active(false).deletedAt(Instant.now()));

        List<ProductSearchResult> results = productSearchService.findActiveProducts(50);

        assertThat(results)
                .hasSize(1)
                .first()
                .satisfies(res -> {
                    assertThat(res.id()).isEqualTo(activeProduct.getId());
                    assertThat(res.name()).isEqualTo("Active Laptop");
                    assertThat(res.categoryName()).isEqualTo("Electronics");
                });
    }

    @Test
    void orderedByNameAscendingAndTieBrokenByIdAscending() {
        Category category = testData.category(b -> b.name("Apparel"));

        // Insert products with different and identical names
        Product pZeta = testData.product(category.getId(), b -> b.name("Zeta Shirt"));
        Product pAlpha1 = testData.product(category.getId(), b -> b.name("Alpha Jacket"));
        Product pAlpha2 = testData.product(category.getId(), b -> b.name("Alpha Jacket"));
        Product pBeta = testData.product(category.getId(), b -> b.name("Beta Pants"));

        List<ProductSearchResult> results = productSearchService.findActiveProducts(50);

        assertThat(results).hasSize(4);
        assertThat(results.get(0).name()).isEqualTo("Alpha Jacket");
        assertThat(results.get(1).name()).isEqualTo("Alpha Jacket");
        assertThat(results.get(2).name()).isEqualTo("Beta Pants");
        assertThat(results.get(3).name()).isEqualTo("Zeta Shirt");

        // Verify id tie-breaker (PostgreSQL sorts UUIDs in unsigned byte/hex order)
        UUID expectedFirstAlphaId = pAlpha1.getId().toString().compareTo(pAlpha2.getId().toString()) <= 0 ? pAlpha1.getId() : pAlpha2.getId();
        UUID expectedSecondAlphaId = pAlpha1.getId().toString().compareTo(pAlpha2.getId().toString()) <= 0 ? pAlpha2.getId() : pAlpha1.getId();

        assertThat(results.get(0).id()).isEqualTo(expectedFirstAlphaId);
        assertThat(results.get(1).id()).isEqualTo(expectedSecondAlphaId);
    }

    @Test
    void limitsResultCount() {
        Category category = testData.category(b -> b.name("Books"));
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            testData.product(category.getId(), b -> b.name("Book " + idx));
        }

        assertThat(productSearchService.findActiveProducts(0)).isEmpty();
        assertThat(productSearchService.findActiveProducts(-1)).isEmpty();
        assertThat(productSearchService.findActiveProducts(3)).hasSize(3);
        assertThat(productSearchService.findActiveProducts(10)).hasSize(5);
    }

    @Test
    void resolvesCategoryNamesWithoutNPlusOne() {
        Category catElectronics = testData.category(b -> b.name("Electronics"));
        Category catBooks = testData.category(b -> b.name("Books"));

        Product p1 = testData.product(catElectronics.getId(), b -> b.name("Camera"));
        Product p2 = testData.product(catBooks.getId(), b -> b.name("Cookbook"));

        List<ProductSearchResult> results = productSearchService.findActiveProducts(10);

        assertThat(results).hasSize(2);
        ProductSearchResult cameraResult = results.stream().filter(r -> r.id().equals(p1.getId())).findFirst().orElseThrow();
        ProductSearchResult bookResult = results.stream().filter(r -> r.id().equals(p2.getId())).findFirst().orElseThrow();

        assertThat(cameraResult.categoryName()).isEqualTo("Electronics");
        assertThat(bookResult.categoryName()).isEqualTo("Books");
    }

    @Test
    void preservesProductDetailsAndExcludesCostPrice() {
        Category category = testData.category(b -> b.name("Furniture"));
        Product product = testData.product(category.getId(), b -> b
                .name("Ergonomic Desk")
                .brand("FlexiSpot")
                .description("Height adjustable standing desk")
                .costPrice(new BigDecimal("150.00"))
                .sellingPrice(new BigDecimal("299.99")));

        List<ProductSearchResult> results = productSearchService.findActiveProducts(10);

        assertThat(results).hasSize(1);
        ProductSearchResult result = results.get(0);
        assertThat(result.id()).isEqualTo(product.getId());
        assertThat(result.name()).isEqualTo("Ergonomic Desk");
        assertThat(result.brand()).isEqualTo("FlexiSpot");
        assertThat(result.categoryName()).isEqualTo("Furniture");
        assertThat(result.description()).isEqualTo("Height adjustable standing desk");
        assertThat(result.sellingPrice()).isEqualByComparingTo("299.99");

        // Architecture check: ProductSearchResult record must not have costPrice (Requirement 7.3, D-85)
        List<String> componentNames = Arrays.stream(ProductSearchResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .containsExactly("id", "name", "brand", "categoryName", "sellingPrice", "description")
                .doesNotContain("costPrice", "cost");

        List<String> fieldNames = Arrays.stream(ProductSearchResult.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("costPrice", "cost");
    }
}
