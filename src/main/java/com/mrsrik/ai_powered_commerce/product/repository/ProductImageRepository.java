package com.mrsrik.ai_powered_commerce.product.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID productId);
}

