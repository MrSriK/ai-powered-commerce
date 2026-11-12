package com.mrsrik.ai_powered_commerce.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Second subclass: must see the same container (same JDBC URL) as {@link PostgresBaseIT}. */
class PostgresBaseSecondIT extends AbstractPostgresIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void usesTheSameContainerAsTheOtherClass() throws Exception {
        String url;
        try (Connection c = dataSource.getConnection()) {
            url = c.getMetaData().getURL();
        }
        SharedContainerRecorder.record("PostgresBaseSecondIT", url);
        assertThat(url).contains(":" + AbstractPostgresIT.POSTGRES.getMappedPort(5432));
        // When the other class ran first in this JVM, its recorded URL must be identical.
        String other = SharedContainerRecorder.URLS.get("PostgresBaseIT");
        if (other != null) {
            assertThat(url).isEqualTo(other);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }
}
