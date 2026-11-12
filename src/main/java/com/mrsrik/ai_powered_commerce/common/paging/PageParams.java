package com.mrsrik.ai_powered_commerce.common.paging;

import com.mrsrik.ai_powered_commerce.common.error.InvalidPaginationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated page parameters. Parsed here instead of by Spring's Pageable resolver because the
 * resolver clamps oversized pages silently, while the requirements demand a 400 (D-21).
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final String ID = "id";

    /** Applies defaults for null values and rejects out-of-range values. */
    public static PageParams of(Integer page, Integer size) {
        int p = page == null ? DEFAULT_PAGE : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw new InvalidPaginationException("Parameter 'page' must be 0 or greater");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw new InvalidPaginationException("Parameter 'size' must be between 1 and " + MAX_SIZE);
        }
        return new PageParams(p, s);
    }

    /**
     * Builds a Pageable with a deterministic order so pages never overlap. An ascending
     * {@code id} order is appended only when the given sort has no {@code id} order; a caller
     * that needs {@code id DESC} passes it in its own sort and it is kept as is.
     */
    public Pageable toPageable(Sort sort) {
        Sort base = sort == null ? Sort.unsorted() : sort;
        Sort effective = base.getOrderFor(ID) != null ? base : base.and(Sort.by(Sort.Direction.ASC, ID));
        return PageRequest.of(page, size, effective);
    }
}
