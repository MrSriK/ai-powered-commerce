package com.mrsrik.ai_powered_commerce.order.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mrsrik.ai_powered_commerce.order.dto.ProgressionResultDto;
import com.mrsrik.ai_powered_commerce.order.service.OrderProgressionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Internal endpoint for scheduled automated order progression (Requirements 12.2, 12.6, 12.7, 12.10).
 * Authenticated via X-Internal-Api-Key header.
 */
@Tag(name = "Internal Orders", description = "Machine-to-machine internal endpoints for order progression")
@RestController
@RequestMapping(path = "/internal/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalOrderController {

    private final OrderProgressionService progressionService;

    public InternalOrderController(OrderProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    @PostMapping("/advance")
    @Operation(summary = "Advance order lifecycle", description = "Internal scheduler endpoint. Requires X-Internal-Api-Key header.")
    @SecurityRequirement(name = "internalApiKey")
    public ProgressionResultDto advanceOrders() {
        return progressionService.advanceAll();
    }
}
