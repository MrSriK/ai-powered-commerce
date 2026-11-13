package com.mrsrik.ai_powered_commerce.product.repository;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.mrsrik.ai_powered_commerce.product.entity.Product;

public final class ProductSpecs {

    private static final char ESCAPE_CHAR = '\\';

    private ProductSpecs() {
    }

    /**
     * Active and not soft-deleted filter. Always applied on public catalog queries (Requirements 1.9, 10.7).
     */
    public static Specification<Product> activeAndNotDeleted() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.isNull(root.get("deletedAt")));
    }

    /**
     * Category filter (Requirement 1.3). If categoryId is null, returns a neutral conjunction predicate.
     */
    public static Specification<Product> inCategory(UUID categoryId) {
        if (categoryId == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    /**
     * Case-insensitive name or description match with wildcard escaping (Requirements 1.4, 1.5).
     * If term is null or blank, returns a neutral conjunction predicate.
     */
    public static Specification<Product> matches(String term) {
        if (term == null || term.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";

        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE_CHAR),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE_CHAR));
    }
}
