package com.mrsrik.ai_powered_commerce.product.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mrsrik.ai_powered_commerce.common.error.CategoryNotFoundException;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintNames;
import com.mrsrik.ai_powered_commerce.common.error.ConstraintViolations;
import com.mrsrik.ai_powered_commerce.common.error.DuplicateSkuException;
import com.mrsrik.ai_powered_commerce.common.error.ProductNotFoundException;
import com.mrsrik.ai_powered_commerce.common.error.ProductReactivationNotSupportedException;
import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.product.dto.CreateProductRequest;
import com.mrsrik.ai_powered_commerce.product.dto.ProductDetailDto;
import com.mrsrik.ai_powered_commerce.product.dto.ProductImageDto;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSummaryDto;
import com.mrsrik.ai_powered_commerce.product.dto.UpdateProductRequest;
import com.mrsrik.ai_powered_commerce.product.entity.Product;
import com.mrsrik.ai_powered_commerce.product.entity.ProductImage;
import com.mrsrik.ai_powered_commerce.product.repository.CategoryRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductImageRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductRepository;
import com.mrsrik.ai_powered_commerce.product.repository.ProductSpecs;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            EntityManager entityManager,
            Clock clock) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public PageResponse<ProductSummaryDto> list(UUID categoryId, String search, PageParams page) {
        Pageable pageable = page.toPageable(Sort.by(Sort.Direction.ASC, "name"));
        Specification<Product> spec = Specification.where(ProductSpecs.activeAndNotDeleted())
                .and(ProductSpecs.inCategory(categoryId))
                .and(ProductSpecs.matches(search));

        Page<Product> productPage = productRepository.findAll(spec, pageable);
        return PageResponse.from(productPage.map(this::toSummaryDto));
    }

    @Override
    public ProductDetailDto get(UUID id) {
        Product product = productRepository.findByIdAndActiveTrueAndDeletedAtIsNull(id)
                .orElseThrow(ProductNotFoundException::new);

        List<ProductImage> images = productImageRepository
                .findByProductIdAndDeletedAtIsNullOrderByDisplayOrderAsc(id);
        List<ProductImageDto> imageDtos = images.stream()
                .map(img -> new ProductImageDto(img.getId(), img.getDisplayOrder(), img.getImageUrl()))
                .toList();

        return toDetailDto(product, imageDtos);
    }

    @Override
    @Transactional
    public ProductDetailDto create(CreateProductRequest request) {
        categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(CategoryNotFoundException::new);

        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException();
        }

        Product product = Product.builder()
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .brand(request.brand())
                .sku(request.sku())
                .costPrice(request.costPrice())
                .sellingPrice(request.sellingPrice())
                .stock(request.stock())
                .active(true)
                .build();

        try {
            Product saved = productRepository.saveAndFlush(product);
            entityManager.refresh(saved);
            return toDetailDto(saved, List.of());
        } catch (DataIntegrityViolationException e) {
            if (isSkuConstraintViolation(e)) {
                throw new DuplicateSkuException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductDetailDto update(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(ProductNotFoundException::new);

        categoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(CategoryNotFoundException::new);

        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new DuplicateSkuException();
        }

        if (!product.isActive() && Boolean.TRUE.equals(request.active())) {
            throw new ProductReactivationNotSupportedException();
        }

        if (product.isActive() && Boolean.FALSE.equals(request.active())) {
            product.setActive(false);
        }

        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setSku(request.sku());
        product.setCostPrice(request.costPrice());
        product.setSellingPrice(request.sellingPrice());
        product.setStock(request.stock());

        try {
            Product saved = productRepository.saveAndFlush(product);
            entityManager.refresh(saved);

            List<ProductImage> images = productImageRepository
                    .findByProductIdAndDeletedAtIsNullOrderByDisplayOrderAsc(id);
            List<ProductImageDto> imageDtos = images.stream()
                    .map(img -> new ProductImageDto(img.getId(), img.getDisplayOrder(), img.getImageUrl()))
                    .toList();

            return toDetailDto(saved, imageDtos);
        } catch (DataIntegrityViolationException e) {
            if (isSkuConstraintViolation(e)) {
                throw new DuplicateSkuException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(ProductNotFoundException::new);

        product.setDeletedAt(Instant.now(clock));
        productRepository.save(product);
    }

    private boolean isSkuConstraintViolation(DataIntegrityViolationException e) {
        return ConstraintViolations.constraintName(e)
                .filter(ConstraintNames.PRODUCTS_SKU::equals)
                .isPresent();
    }

    private ProductSummaryDto toSummaryDto(Product product) {
        return new ProductSummaryDto(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getBrand(),
                product.getSku(),
                product.getCostPrice(),
                product.getSellingPrice(),
                product.getStock());
    }

    private ProductDetailDto toDetailDto(Product product, List<ProductImageDto> images) {
        return new ProductDetailDto(
                product.getId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getSku(),
                product.getCostPrice(),
                product.getSellingPrice(),
                product.getStock(),
                images);
    }
}
