package com.mrsrik.ai_powered_commerce.product.service;

import java.util.List;

import com.mrsrik.ai_powered_commerce.product.dto.ProductSearchResult;

/**
 * Read-only contract for the AI shopping assistant to query catalog products (Requirements 7.3, 7.10).
 * Exposes query operations only and performs no writes.
 */
public interface ProductSearchService {

    List<ProductSearchResult> findActiveProducts(int limit);
}

