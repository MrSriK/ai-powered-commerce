package com.mrsrik.ai_powered_commerce.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.product.entity.Category;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;

/** Confirms every name in {@link ConstraintNames} against the migrated schema (D-39, D-76). */
class ConstraintNamesIT extends AbstractPostgresIT {

    /** Prints every unique constraint and unique index of schema public (for the task report). */
    @Test
    void listsAllUniqueConstraintsAndIndexes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT conrelid::regclass::text AS tbl, conname AS name, 'constraint' AS kind, "
                        + "pg_get_constraintdef(oid) AS def FROM pg_constraint "
                        + "WHERE connamespace = 'public'::regnamespace AND contype = 'u' "
                        + "UNION ALL "
                        + "SELECT tablename, indexname, 'unique index', indexdef FROM pg_indexes i "
                        + "WHERE schemaname = 'public' AND indexdef LIKE 'CREATE UNIQUE INDEX%' "
                        + "AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conname = i.indexname "
                        + "AND c.connamespace = 'public'::regnamespace) "
                        + "AND indexname NOT LIKE '%\\_pkey' "
                        + "ORDER BY 1, 2");
        rows.forEach(r -> System.out.println("UNIQUE: " + r));
        assertThat(rows).isNotEmpty();
    }

    @Test
    void usersExternalAuthIdIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.USERS_EXTERNAL_AUTH_ID, "users", "UNIQUE (external_auth_id)");
    }

    @Test
    void usersEmailIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.USERS_EMAIL, "users", "UNIQUE (email)");
    }

    @Test
    void productsSkuIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.PRODUCTS_SKU, "products", "UNIQUE (sku)");
    }

    @Test
    void categoriesNameIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.CATEGORIES_NAME, "categories", "UNIQUE (name)");
    }

    @Test
    void ordersTransactionIdIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.ORDERS_TRANSACTION_ID, "orders", "UNIQUE (transaction_id)");
    }

    @Test
    void cartsUserIdIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.CARTS_USER_ID, "carts", "UNIQUE (user_id)");
    }

    @Test
    void cartItemsCartAndProductIsUniqueConstraint() {
        assertUniqueConstraint(ConstraintNames.CART_ITEMS_CART_PRODUCT, "cart_items",
                "UNIQUE (cart_id, product_id)");
    }

    @Test
    void duplicateExternalAuthIdReportsConstraint() {
        User u = testData.user();
        assertViolates(ConstraintNames.USERS_EXTERNAL_AUTH_ID,
                () -> testData.user(b -> b.externalAuthId(u.getExternalAuthId())));
    }

    @Test
    void duplicateEmailReportsConstraint() {
        User u = testData.user();
        assertViolates(ConstraintNames.USERS_EMAIL, () -> testData.user(b -> b.email(u.getEmail())));
    }

    @Test
    void duplicateSkuReportsConstraint() {
        UUID categoryId = testData.category().getId();
        String sku = testData.product(categoryId).getSku();
        assertViolates(ConstraintNames.PRODUCTS_SKU, () -> testData.product(categoryId, b -> b.sku(sku)));
    }

    @Test
    void duplicateCategoryNameReportsConstraint() {
        Category c = testData.category();
        assertViolates(ConstraintNames.CATEGORIES_NAME, () -> testData.category(b -> b.name(c.getName())));
    }

    @Test
    void duplicateTransactionIdReportsConstraint() {
        UUID userId = testData.user().getId();
        String txn = testData.order(userId, OrderStatus.PENDING).getTransactionId();
        assertViolates(ConstraintNames.ORDERS_TRANSACTION_ID,
                () -> testData.order(userId, OrderStatus.PENDING, b -> b.transactionId(txn)));
    }

    @Test
    void duplicateCartForUserReportsConstraint() {
        UUID userId = testData.user().getId();
        testData.cart(userId);
        assertViolates(ConstraintNames.CARTS_USER_ID, () -> testData.cart(userId));
    }

    @Test
    void duplicateCartItemReportsConstraint() {
        UUID userId = testData.user().getId();
        UUID cartId = testData.cart(userId).getId();
        UUID productId = testData.product(testData.category().getId()).getId();
        testData.cartItem(cartId, productId, 1);
        assertViolates(ConstraintNames.CART_ITEMS_CART_PRODUCT,
                () -> testData.cartItem(cartId, productId, 2));
    }

    @Test
    void rawJdbcDuplicateEmailGivesSpringExceptionWithPostgresConstraint() {
        jdbcTemplate.update("INSERT INTO users (id, email, role) VALUES (?, 'dup@example.test', 'CUSTOMER')",
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (id, email, role) VALUES (?, 'dup@example.test', 'CUSTOMER')",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(ConstraintViolations.constraintName(e))
                        .contains(ConstraintNames.USERS_EMAIL));
    }

    private void assertViolates(String expectedConstraint, Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(e -> assertThat(ConstraintViolations.constraintName(e))
                        .contains(expectedConstraint));
    }

    /** Exactly one UNIQUE constraint with this name exists, on the table, with this definition. */
    private void assertUniqueConstraint(String name, String table, String expectedDef) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT conrelid::regclass::text AS tbl, contype::text AS kind, "
                        + "pg_get_constraintdef(oid) AS def FROM pg_constraint "
                        + "WHERE connamespace = 'public'::regnamespace AND conname = ?",
                name);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("tbl")).isEqualTo(table);
        assertThat(rows.get(0).get("kind")).isEqualTo("u");
        assertThat(rows.get(0).get("def")).isEqualTo(expectedDef);
    }
}
