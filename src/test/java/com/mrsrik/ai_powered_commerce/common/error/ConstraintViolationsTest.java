package com.mrsrik.ai_powered_commerce.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationsTest {

    private static PSQLException psql(String constraint) {
        String raw = "SERROR\0C23505\0Mduplicate key\0"
                + (constraint == null ? "" : "n" + constraint + "\0") + "\0";
        return new PSQLException(new ServerErrorMessage(raw));
    }

    @Test
    void findsConstraintThroughSpringWrapper() {
        Throwable t = new DataIntegrityViolationException("wrapped", psql("users_email_key"));
        assertThat(ConstraintViolations.constraintName(t)).contains("users_email_key");
    }

    @Test
    void findsConstraintDeepInChain() {
        Throwable t = new RuntimeException("a", new IllegalStateException("b", psql("products_sku_key")));
        assertThat(ConstraintViolations.constraintName(t)).contains("products_sku_key");
    }

    @Test
    void emptyWhenNoConstraintOrNoPostgresError() {
        assertThat(ConstraintViolations.constraintName(psql(null))).isEmpty();
        assertThat(ConstraintViolations.constraintName(new RuntimeException("x"))).isEmpty();
        assertThat(ConstraintViolations.constraintName(null)).isEmpty();
    }
}
