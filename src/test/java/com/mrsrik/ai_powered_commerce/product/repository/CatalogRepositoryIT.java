package com.mrsrik.ai_powered_commerce.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;

class CatalogRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Test
    void productFindByIdAndActiveTrueAndDeletedAtIsNull() {
        Category category = testData.category();
        Product active = testData.product(category.getId());
        Product inactive = testData.product(category.getId(), b -> b.active(false));
        Product deleted = testData.product(category.getId(), b -> b.deletedAt(Instant.now()));

        assertThat(productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(active.getId()))
                .hasValueSatisfying(p -> assertThat(p.getId()).isEqualTo(active.getId()));
        assertThat(productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(inactive.getId())).isEmpty();
        assertThat(productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(deleted.getId())).isEmpty();
        assertThat(productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(UUID.randomUUID())).isEmpty();
    }

    @Test
    void productFindByIdAndDeletedAtIsNullAllowsInactive() {
        Category category = testData.category();
        Product active = testData.product(category.getId());
        Product inactive = testData.product(category.getId(), b -> b.active(false));
        Product deleted = testData.product(category.getId(), b -> b.deletedAt(Instant.now()));

        assertThat(productRepository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(productRepository.findByIdAndDeletedAtIsNull(inactive.getId())).isPresent();
        assertThat(productRepository.findByIdAndDeletedAtIsNull(deleted.getId())).isEmpty();
    }

    @Test
    void productExistsBySkuIncludesSoftDeleted() {
        Category category = testData.category();
        Product active = testData.product(category.getId(), b -> b.sku("SKU-ACTIVE"));
        Product deleted = testData.product(category.getId(), b -> b.sku("SKU-DELETED").deletedAt(Instant.now()));

        assertThat(productRepository.existsBySku("SKU-ACTIVE")).isTrue();
        assertThat(productRepository.existsBySku("SKU-DELETED")).isTrue();
        assertThat(productRepository.existsBySku("SKU-OTHER")).isFalse();

        assertThat(productRepository.existsBySkuAndIdNot("SKU-ACTIVE", active.getId())).isFalse();
        assertThat(productRepository.existsBySkuAndIdNot("SKU-ACTIVE", UUID.randomUUID())).isTrue();
        assertThat(productRepository.existsBySkuAndIdNot("SKU-DELETED", deleted.getId())).isFalse();
        assertThat(productRepository.existsBySkuAndIdNot("SKU-DELETED", UUID.randomUUID())).isTrue();
    }

    @Test
    void productExistsByCategoryIdAndActiveTrueAndDeletedAtIsNullCountsOnlyActiveNonDeleted() {
        Category emptyCategory = testData.category();
        Category inactiveOnlyCategory = testData.category();
        testData.product(inactiveOnlyCategory.getId(), b -> b.active(false));

        Category deletedOnlyCategory = testData.category();
        testData.product(deletedOnlyCategory.getId(), b -> b.deletedAt(Instant.now()));

        Category activeCategory = testData.category();
        testData.product(activeCategory.getId(), b -> b.active(true));

        assertThat(productRepository.existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(emptyCategory.getId())).isFalse();
        assertThat(productRepository.existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(inactiveOnlyCategory.getId())).isFalse();
        assertThat(productRepository.existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(deletedOnlyCategory.getId())).isFalse();
        assertThat(productRepository.existsByCategoryIdAndActiveTrueAndDeletedAtIsNull(activeCategory.getId())).isTrue();
    }

    @Test
    void categoryRepositoryQueries() {
        Category cB = testData.category(b -> b.name("Bravo"));
        Category cA = testData.category(b -> b.name("Alpha"));
        Category cDeleted = testData.category(b -> b.name("Charlie").deletedAt(Instant.now()));

        List<Category> nonDeleted = categoryRepository.findByDeletedAtIsNullOrderByNameAsc();
        assertThat(nonDeleted)
                .extracting(Category::getName)
                .containsExactly("Alpha", "Bravo");

        assertThat(categoryRepository.findByIdAndDeletedAtIsNull(cA.getId())).isPresent();
        assertThat(categoryRepository.findByIdAndDeletedAtIsNull(cDeleted.getId())).isEmpty();

        assertThat(categoryRepository.existsByNameIgnoreCase("alpha")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("ALPHA")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("charlie")).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCase("delta")).isFalse();

        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("alpha", cA.getId())).isFalse();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("alpha", cB.getId())).isTrue();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("charlie", cDeleted.getId())).isFalse();
        assertThat(categoryRepository.existsByNameIgnoreCaseAndIdNot("charlie", cA.getId())).isTrue();
    }

    @Test
    void productImageRepositoryQueries() {
        Category category = testData.category();
        Product product1 = testData.product(category.getId());
        Product product2 = testData.product(category.getId());

        ProductImage img2 = testData.productImage(product1.getId(), 2);
        ProductImage img1 = testData.productImage(product1.getId(), 1);
        testData.productImage(product1.getId(), 3, b -> b.deletedAt(Instant.now()));
        testData.productImage(product2.getId(), 1);

        List<ProductImage> images = productImageRepository
                .findByProductIdAndDeletedAtIsNullOrderByDisplayOrderAsc(product1.getId());

        assertThat(images).extracting(ProductImage::getId).containsExactly(img1.getId(), img2.getId());
    }

    @Test
    void productSpecsFiltering() {
        Category c1 = testData.category();
        Category c2 = testData.category();

        Product p1 = testData.product(c1.getId(), b -> b.name("Wireless Mouse").description("Ergonomic 2.4G mouse"));
        Product p2 = testData.product(c1.getId(), b -> b.name("USB-C Keyboard").description("Mechanical wireless keyboard"));
        Product p3 = testData.product(c2.getId(), b -> b.name("Mouse Pad").description("Large smooth surface"));
        testData.product(c1.getId(), b -> b.name("Wireless Headset").active(false));
        testData.product(c1.getId(), b -> b.name("Wireless Charger").deletedAt(Instant.now()));

        // Active and not deleted
        Specification<Product> activeSpec = ProductSpecs.activeAndNotDeleted();
        Page<Product> activeProducts = productRepository.findAll(activeSpec, PageRequest.of(0, 10));
        assertThat(activeProducts.getContent()).extracting(Product::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId(), p3.getId());

        // Category filter
        Specification<Product> c1Spec = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.inCategory(c1.getId()));
        Page<Product> c1Products = productRepository.findAll(c1Spec, PageRequest.of(0, 10));
        assertThat(c1Products.getContent()).extracting(Product::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());

        // Search matches name or description (case-insensitive)
        Specification<Product> searchMouse = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.matches("mouse"));
        Page<Product> mouseProducts = productRepository.findAll(searchMouse, PageRequest.of(0, 10));
        assertThat(mouseProducts.getContent()).extracting(Product::getId)
                .containsExactlyInAnyOrder(p1.getId(), p3.getId());

        Specification<Product> searchWireless = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.matches("WIRELESS"));
        Page<Product> wirelessProducts = productRepository.findAll(searchWireless, PageRequest.of(0, 10));
        assertThat(wirelessProducts.getContent()).extracting(Product::getId)
                .containsExactlyInAnyOrder(p1.getId(), p2.getId());

        // Literal wildcard escaping: searching for "%" should not match everything
        Product literalWildcard = testData.product(c1.getId(), b -> b.name("50% Off Special").description("Promo item"));
        Specification<Product> searchPercent = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.matches("%"));
        Page<Product> percentProducts = productRepository.findAll(searchPercent, PageRequest.of(0, 10));
        assertThat(percentProducts.getContent()).extracting(Product::getId)
                .containsExactly(literalWildcard.getId());

        // Null or blank search adds no predicate
        Specification<Product> blankSearch = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.matches("   "));
        Page<Product> allActive = productRepository.findAll(blankSearch, PageRequest.of(0, 10));
        assertThat(allActive.getContent()).hasSize(4);
    }
}
