package com.mrsrik.ai_powered_commerce.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mrsrik.ai_powered_commerce.cart.entity.Cart;
import com.mrsrik.ai_powered_commerce.cart.entity.CartItem;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.entity.OrderItem;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;
import com.mrsrik.ai_powered_commerce.user.entity.User;
import com.mrsrik.ai_powered_commerce.user.enums.Role;

/** Proves the integration test base (task 5.1). Named *IT on purpose: Surefire must run it. */
class PostgresBaseIT extends AbstractPostgresIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayRanAllNineMigrationsAndSchemaExists() {
        Integer ok = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        Integer all = jdbcTemplate.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class);
        assertThat(ok).isEqualTo(9);
        assertThat(all).isEqualTo(9);
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class))
                .contains("products", "orders", "users", "carts");
    }

    @Test
    void datasourceIsTheTestcontainersDatabase() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            assertThat(url).doesNotContain(":5433").startsWith("jdbc:postgresql://");
            assertThat(url).contains(":" + AbstractPostgresIT.POSTGRES.getMappedPort(5432));
            assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(17);
        }
        assertThat(jdbcTemplate.queryForObject("SHOW server_version", String.class)).startsWith("17");
        SharedContainerRecorder.record("PostgresBaseIT", jdbcUrl());
    }

    @Test
    void cleanerEmptiesTablesBeforeEachTest_first() {
        assertCleanThenInsertOneUser();
    }

    @Test
    void cleanerEmptiesTablesBeforeEachTest_second() {
        assertCleanThenInsertOneUser();
    }

    private void assertCleanThenInsertOneUser() {
        assertThat(count("users")).isZero();
        assertThat(count("categories")).isZero();
        testData.user();
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("flyway_schema_history")).isEqualTo(9);
    }

    @Test
    void testDataBuildsFullGraphWithScalarForeignKeys() {
        User user = testData.user(Role.ADMIN);
        User customer = testData.user();
        Category category = testData.category();
        Product product = testData.product(category.getId());
        ProductImage image = testData.productImage(product.getId(), 1);
        Cart cart = testData.cart(customer.getId());
        CartItem cartItem = testData.cartItem(cart.getId(), product.getId(), 3);
        Order order = testData.order(customer.getId(), OrderStatus.PENDING);
        OrderItem orderItem = testData.orderItem(order.getId(), product.getId(), 2);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getEmail()).isNotEqualTo(customer.getEmail());
        assertThat(user.getExternalAuthId()).isNotEqualTo(customer.getExternalAuthId());

        record Row(UUID id, Object createdAt, Object updatedAt) { }
        List<Row> rows = List.of(
                new Row(user.getId(), user.getCreatedAt(), user.getUpdatedAt()),
                new Row(category.getId(), category.getCreatedAt(), category.getUpdatedAt()),
                new Row(product.getId(), product.getCreatedAt(), product.getUpdatedAt()),
                new Row(image.getId(), image.getCreatedAt(), image.getUpdatedAt()),
                new Row(cart.getId(), cart.getCreatedAt(), cart.getUpdatedAt()),
                new Row(cartItem.getId(), cartItem.getCreatedAt(), cartItem.getUpdatedAt()),
                new Row(order.getId(), order.getCreatedAt(), order.getUpdatedAt()),
                new Row(orderItem.getId(), orderItem.getCreatedAt(), orderItem.getUpdatedAt()));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.id()).isNotNull();
            assertThat(r.createdAt()).isNotNull();
            assertThat(r.updatedAt()).isNotNull();
        });

        assertThat(scalar("SELECT category_id FROM products")).isEqualTo(category.getId());
        assertThat(scalar("SELECT product_id FROM product_images")).isEqualTo(product.getId());
        assertThat(scalar("SELECT user_id FROM carts")).isEqualTo(customer.getId());
        assertThat(scalar("SELECT cart_id FROM cart_items")).isEqualTo(cart.getId());
        assertThat(scalar("SELECT product_id FROM cart_items")).isEqualTo(product.getId());
        assertThat(scalar("SELECT user_id FROM orders")).isEqualTo(customer.getId());
        assertThat(scalar("SELECT order_id FROM order_items")).isEqualTo(order.getId());
        assertThat(scalar("SELECT product_id FROM order_items")).isEqualTo(product.getId());
    }

    @Test
    void testDataSupportsEveryOrderStatusAndRepeatedCalls() {
        User user = testData.user();
        for (OrderStatus status : OrderStatus.values()) {
            testData.order(user.getId(), status);
        }
        assertThat(count("orders")).isEqualTo(OrderStatus.values().length);
        Category category = testData.category();
        testData.product(category.getId());
        testData.product(category.getId());
        assertThat(count("products")).isEqualTo(2);
    }

    private String jdbcUrl() {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private Object scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, UUID.class);
    }
}
