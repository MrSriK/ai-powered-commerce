package com.mrsrik.ai_powered_commerce.common.error;

import java.util.Optional;

/**
 * Extracts the violated constraint name from a PostgreSQL error.
 *
 * <p>
 * The PostgreSQL JDBC driver is a runtime-scope dependency, so it cannot be
 * referenced at
 * compile time. The two driver methods used
 * ({@code PSQLException.getServerErrorMessage()} and
 * {@code ServerErrorMessage.getConstraint()}) are therefore called by
 * reflection.
 */
public final class ConstraintViolations {

    private static final String PSQL_EXCEPTION = "org.postgresql.util.PSQLException";

    private ConstraintViolations() {
    }

    /**
     * Walks the cause chain (for example {@code DataIntegrityViolationException} ->
     * {@code PSQLException}) and returns the constraint reported by the first
     * PostgreSQL
     * server error that carries one, or empty if there is none.
     */
    public static Optional<String> constraintName(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (PSQL_EXCEPTION.equals(t.getClass().getName())) {
                String name = read(t);
                if (name != null) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    private static String read(Throwable psqlException) {
        try {
            Object server = psqlException.getClass().getMethod("getServerErrorMessage").invoke(psqlException);
            if (server == null) {
                return null;
            }
            return (String) server.getClass().getMethod("getConstraint").invoke(server);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
