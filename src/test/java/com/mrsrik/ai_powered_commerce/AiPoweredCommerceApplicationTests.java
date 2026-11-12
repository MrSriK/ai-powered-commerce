package com.mrsrik.ai_powered_commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;

class AiPoweredCommerceApplicationTests extends AbstractPostgresIT {

	@Autowired
	private Environment environment;

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void hibernateDdlAutoIsValidate() {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
	}

	@Test
	void flywayHistoryIsUniqueAscendingAndSuccessful() throws Exception {
		List<Integer> versions = jdbcTemplate.queryForList(
				"SELECT version::int FROM flyway_schema_history WHERE version IS NOT NULL "
						+ "ORDER BY installed_rank",
				Integer.class);
		long failed = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success = false", Long.class);
		int migrationFiles = new PathMatchingResourcePatternResolver()
				.getResources("classpath*:db/migration/V*__*.sql").length;

		assertThat(failed).isZero();
		assertThat(versions).doesNotHaveDuplicates().isSorted();
		assertThat(versions.get(versions.size() - 1)).isEqualTo(migrationFiles);
		assertThat(versions).hasSize(migrationFiles);
	}

	@Test
	void contextUsesTestcontainersDatabaseNotDevDatabase() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			String url = connection.getMetaData().getURL();
			assertThat(url).startsWith("jdbc:postgresql://").doesNotContain(":5433/");
		}
	}

}
