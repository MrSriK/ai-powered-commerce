package com.mrsrik.ai_powered_commerce.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Tests migration V9 (orders.shipped_at, decision D-80) against data written under V8.
 *
 * <p>Plain JUnit with its own throwaway PostgreSQL container: the shared base class
 * migrates to the latest version, so it cannot stop at V8. The class migrates to 8, seeds
 * orders, migrates to 9 once, and every test then only reads (or inserts new rows).
 */
class V9MigrationIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SHIPPED_ID = UUID.randomUUID();
    private static final UUID DELIVERED_ID = UUID.randomUUID();
    private static final UUID PENDING_ID = UUID.randomUUID();
    private static final UUID CANCELLED_ID = UUID.randomUUID();

    private static final Instant BASE = Instant.now().minus(30, ChronoUnit.DAYS);

    /** updated_at of each seeded order as stored before V9, keyed by order id. */
    private static final java.util.Map<UUID, Instant> OLD_UPDATED_AT = new java.util.HashMap<>();

    private static Instant migratedAt;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        POSTGRES.start();

        flyway("8").migrate();

        try (Connection c = connection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (id, role) VALUES (?, 'CUSTOMER')")) {
                ps.setObject(1, USER_ID);
                ps.executeUpdate();
            }
            insertOrder(c, SHIPPED_ID, "SHIPPED", BASE.plus(1, ChronoUnit.DAYS));
            insertOrder(c, DELIVERED_ID, "DELIVERED", BASE.plus(2, ChronoUnit.DAYS));
            insertOrder(c, PENDING_ID, "PENDING", BASE.plus(3, ChronoUnit.DAYS));
            insertOrder(c, CANCELLED_ID, "CANCELLED", BASE.plus(4, ChronoUnit.DAYS));

            // Read back what is really stored, so comparisons are exact.
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT id, updated_at FROM orders")) {
                while (rs.next()) {
                    OLD_UPDATED_AT.put(rs.getObject("id", UUID.class),
                            rs.getObject("updated_at", OffsetDateTime.class).toInstant());
                }
            }
        }
        assertThat(OLD_UPDATED_AT).hasSize(4);

        migratedAt = Instant.now().minusSeconds(1);
        flyway("9").migrate();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void backfillsShippedAtForShippedAndDeliveredOrders() throws Exception {
        assertThat(shippedAt(SHIPPED_ID)).isEqualTo(OLD_UPDATED_AT.get(SHIPPED_ID));
        assertThat(shippedAt(DELIVERED_ID)).isEqualTo(OLD_UPDATED_AT.get(DELIVERED_ID));
    }

    @Test
    void leavesShippedAtNullForPendingAndCancelledOrders() throws Exception {
        assertThat(shippedAt(PENDING_ID)).isNull();
        assertThat(shippedAt(CANCELLED_ID)).isNull();
    }

    @Test
    void addsNullableTimestamptzColumn() throws Exception {
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT data_type, is_nullable FROM information_schema.columns "
                                + "WHERE table_name = 'orders' AND column_name = 'shipped_at'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("data_type")).isEqualTo("timestamp with time zone");
            assertThat(rs.getString("is_nullable")).isEqualTo("YES");
        }
    }

    @Test
    void checkRejectsShippedAndDeliveredWithoutShippedAt() throws Exception {
        try (Connection c = connection()) {
            for (String status : List.of("SHIPPED", "DELIVERED")) {
                assertThatThrownBy(() -> insertOrder(c, UUID.randomUUID(), status, null, BASE))
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo("23514");
                            assertThat(e.getMessage()).contains("chk_orders_shipped_at_required");
                        });
            }
        }
    }

    @Test
    void checkAllowsOtherStatusesWithoutShippedAtAndShippedWithIt() throws Exception {
        try (Connection c = connection()) {
            for (String status : List.of("PENDING", "CONFIRMED", "CANCELLED")) {
                insertOrder(c, UUID.randomUUID(), status, null, BASE);
            }
            insertOrder(c, UUID.randomUUID(), "SHIPPED", BASE, BASE);
        }
    }

    @Test
    void createsPartialProgressionIndex() throws Exception {
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT indexdef FROM pg_indexes "
                                + "WHERE tablename = 'orders' AND indexname = 'idx_orders_progression'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("indexdef"))
                    .contains("(status, shipped_at)")
                    .contains("deleted_at IS NULL")
                    .contains("PENDING")
                    .contains("CONFIRMED")
                    .contains("SHIPPED")
                    .doesNotContain("DELIVERED");
        }
    }

    @Test
    void flywayHistoryShowsVersionsOneToNineOnceEachAllSuccessful() throws Exception {
        List<String> versions = new ArrayList<>();
        try (Connection c = connection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT version, success FROM flyway_schema_history "
                                + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            while (rs.next()) {
                assertThat(rs.getBoolean("success")).isTrue();
                versions.add(rs.getString("version"));
            }
        }
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    void backfillMovesUpdatedAtOfBackfilledRowsOnlyBecauseOfTrigger() throws Exception {
        for (UUID id : List.of(SHIPPED_ID, DELIVERED_ID)) {
            Instant now = updatedAt(id);
            assertThat(now).isAfter(OLD_UPDATED_AT.get(id));
            assertThat(now).isAfterOrEqualTo(migratedAt);
        }
        for (UUID id : List.of(PENDING_ID, CANCELLED_ID)) {
            assertThat(updatedAt(id)).isEqualTo(OLD_UPDATED_AT.get(id));
        }
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Insert for the V8 schema (no shipped_at column yet). */
    private static void insertOrder(Connection c, UUID id, String status, Instant updatedAt)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (id, user_id, shipping_name, shipping_phone_number, "
                        + "shipping_address_line_1, city, state, postal_code, country, "
                        + "transaction_id, total_amount, total_items, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Test User', '5551234567', '1 Main St', 'City', 'State', "
                        + "'12345', 'US', ?, 10.00, 1, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, USER_ID);
            ps.setString(3, "txn-" + id);
            ps.setString(4, status);
            ps.setObject(5, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
            ps.setObject(6, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    /** Insert for the V9 schema, with an explicit shipped_at (may be null). */
    private static void insertOrder(Connection c, UUID id, String status, Instant shippedAt,
            Instant updatedAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (id, user_id, shipping_name, shipping_phone_number, "
                        + "shipping_address_line_1, city, state, postal_code, country, "
                        + "transaction_id, total_amount, total_items, status, shipped_at, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, 'Test User', '5551234567', '1 Main St', 'City', 'State', "
                        + "'12345', 'US', ?, 10.00, 1, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, USER_ID);
            ps.setString(3, "txn-" + id);
            ps.setString(4, status);
            ps.setObject(5, shippedAt == null ? null : OffsetDateTime.ofInstant(shippedAt, ZoneOffset.UTC),
                    java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            ps.setObject(6, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
            ps.setObject(7, OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
            ps.executeUpdate();
        }
    }

    private static Instant shippedAt(UUID id) throws SQLException {
        return readInstant(id, "shipped_at");
    }

    private static Instant updatedAt(UUID id) throws SQLException {
        return readInstant(id, "updated_at");
    }

    private static Instant readInstant(UUID id, String column) throws SQLException {
        try (Connection c = connection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT " + column + " FROM orders WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                OffsetDateTime value = rs.getObject(1, OffsetDateTime.class);
                return value == null ? null : value.toInstant();
            }
        }
    }
}
