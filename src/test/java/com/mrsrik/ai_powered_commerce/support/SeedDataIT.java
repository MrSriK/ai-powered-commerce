package com.mrsrik.ai_powered_commerce.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration test for demo seed data (Task 42.2, Requirements 9.5, 9.6, 10.3).
 *
 * <p>Runs against its own dedicated PostgreSQL container without truncating tables in
 * {@code @BeforeEach}, preserving the initial state populated by Flyway migration V10.
 * Verifies that:
 * <ul>
 *   <li>Hibernate schema validation passes on startup against the fully migrated schema</li>
 *   <li>Categories inferred from image assets exist and are active</li>
 *   <li>Active products and primary product images exist</li>
 *   <li>Every product's image URL points to a local file in the {@code images/} directory</li>
 *   <li>No user data or user-scoped data (orders, carts, messages) is seeded</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ SeedDataIT.ContainerConfig.class, FakeAiProvider.class })
class SeedDataIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {

        @Bean(destroyMethod = "")
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return POSTGRES;
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void hibernateValidationPassedAndEntityManagerIsOpen() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }

    @Test
    void flywayHistoryIncludesV10Successfully() {
        Boolean v10Success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '10'", Boolean.class);
        assertThat(v10Success).isTrue();
    }

    @Test
    void seededCategoriesExistAndMatchInferredImageTypes() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE deleted_at IS NULL", Long.class);
        assertThat(count).isEqualTo(5);

        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM categories WHERE deleted_at IS NULL ORDER BY name", String.class);
        assertThat(names).containsExactlyInAnyOrder(
                "Audio", "Cameras", "Footwear", "Personal Care", "Skincare");
    }

    @Test
    void seededProductsAreActiveAndLinkedToCategories() {
        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM products WHERE deleted_at IS NULL AND is_active = true", Long.class);
        assertThat(activeCount).isEqualTo(5);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT p.id, p.name, p.brand, p.sku, p.cost_price, p.selling_price, p.stock, c.name AS category_name "
                        + "FROM products p "
                        + "JOIN categories c ON p.category_id = c.id "
                        + "WHERE p.deleted_at IS NULL AND p.is_active = true");

        assertThat(rows).hasSize(5);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("name")).isNotNull();
            assertThat(row.get("brand")).isNotNull();
            assertThat(row.get("sku")).isNotNull();
            assertThat(row.get("category_name")).isNotNull();
        }
    }

    @Test
    void seededProductImagesReferenceLocalFiles() {
        Long imageCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_images WHERE deleted_at IS NULL", Long.class);
        assertThat(imageCount).isEqualTo(5);

        List<Map<String, Object>> images = jdbcTemplate.queryForList(
                "SELECT product_id, display_order, image_url FROM product_images WHERE deleted_at IS NULL");

        assertThat(images).hasSize(5);
        for (Map<String, Object> img : images) {
            assertThat(img.get("display_order")).isEqualTo(1);
            String url = (String) img.get("image_url");
            assertThat(url).startsWith("images/");
            assertThat(Files.isRegularFile(Path.of(url)))
                    .as("File referenced by imageUrl '%s' should exist on disk", url)
                    .isTrue();
        }
    }

    @Test
    void noUserDataOrUserScopedDataSeeded() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM carts", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cart_items", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ai_conversations", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ai_messages", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM addresses", Long.class)).isZero();
    }
}
