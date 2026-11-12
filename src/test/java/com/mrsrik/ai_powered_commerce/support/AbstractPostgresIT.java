package com.mrsrik.ai_powered_commerce.support;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
 * Base class for integration tests on a real PostgreSQL (D-50).
 *
 * <p>One container is started once per JVM (static initializer) and is wired to the
 * datasource with {@code @ServiceConnection}, overriding the localhost:5433 defaults.
 * Real Flyway migrations run on startup. Before every test, all tables in schema
 * {@code public} except {@code flyway_schema_history} are truncated, so seed data from
 * V10 is not present and tests start from an empty database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ AbstractPostgresIT.ContainerConfig.class, TestData.class })
public abstract class AbstractPostgresIT {

    /** Same major version as docker-compose.yml. */
    static final String POSTGRES_IMAGE = "postgres:17";

    /** Shared by every test class in the JVM. Ryuk removes it when the JVM exits. */
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestData testData;

    @BeforeEach
    void cleanDatabase() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);
        if (tables.isEmpty()) {
            return;
        }
        String quoted = String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList());
        jdbcTemplate.execute("TRUNCATE TABLE " + quoted + " RESTART IDENTITY CASCADE");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {

        // destroyMethod "" : the container outlives any single Spring context.
        @Bean(destroyMethod = "")
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return POSTGRES;
        }
    }
}
