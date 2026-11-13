package com.mrsrik.ai_powered_commerce.product.service;

import java.util.UUID;

import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.product.dto.CreateProductRequest;
import com.mrsrik.ai_powered_commerce.product.dto.ProductDetailDto;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSummaryDto;
import com.mrsrik.ai_powered_commerce.product.dto.UpdateProductRequest;

public interface ProductService {

    /**
     * Lists active, non-deleted products with optional category and search filters, paginated and sorted by name ASC, id ASC.
     */
    PageResponse<ProductSummaryDto> list(UUID categoryId, String search, PageParams page);

    /**
     * Retrieves an active, non-deleted product with its ordered images.
     * Throws ProductNotFoundException (404) if missing, inactive, or soft-deleted.
     */
    ProductDetailDto get(UUID id);

    /**
     * Creates a new active product. Throws CategoryNotFoundException (404) if category does not exist or is soft-deleted,
     * or DuplicateSkuException (409) if the SKU already exists (including on soft-deleted products).
     */
    ProductDetailDto create(CreateProductRequest request);

    /**
     * Updates an existing non-deleted product. Throws ProductNotFoundException (404) if missing or soft-deleted,
     * CategoryNotFoundException (404) if category is missing or soft-deleted, DuplicateSkuException (409) on SKU conflict,
     * or ProductReactivationNotSupportedException (409) if attempting to reactivate an inactive product.
     */
    ProductDetailDto update(UUID id, UpdateProductRequest request);

    /**
     * Soft-deletes an existing product by setting deletedAt. Throws ProductNotFoundException (404) if missing or already deleted.
     */
    void delete(UUID id);
}
