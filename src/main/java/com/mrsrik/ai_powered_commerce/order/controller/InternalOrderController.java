package com.mrsrik.ai_powered_commerce.order.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.order.dto.ProgressionResultDto;
import com.mrsrik.ai_powered_commerce.order.service.OrderProgressionService;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Internal endpoint for scheduled automated order progression (Requirements 12.2, 12.6, 12.7, 12.10).
 * Hidden from public OpenAPI documentation and served exclusively through the internal security filter chain.
 */
@Hidden
@RestController
@RequestMapping(path = "/internal/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalOrderController {

    private final OrderProgressionService progressionService;

    public InternalOrderController(OrderProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    @PostMapping("/advance")
    public ProgressionResultDto advanceOrders() {
        return progressionService.advanceAll();
    }
}
