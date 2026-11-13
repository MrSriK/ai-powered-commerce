package com.mrsrik.ai_powered_commerce.product.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.common.paging.PageParams;
import com.mrsrik.ai_powered_commerce.common.paging.PageResponse;
import com.mrsrik.ai_powered_commerce.product.dto.CreateProductRequest;
import com.mrsrik.ai_powered_commerce.product.dto.ProductDetailDto;
import com.mrsrik.ai_powered_commerce.product.dto.ProductSummaryDto;
import com.mrsrik.ai_powered_commerce.product.dto.UpdateProductRequest;
import com.mrsrik.ai_powered_commerce.product.service.ProductService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductSummaryDto> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @Size(max = 255) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return productService.list(categoryId, search, PageParams.of(page, size));
    }

    @GetMapping("/{id}")
    public ProductDetailDto get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailDto create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDetailDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }
}
