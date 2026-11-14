package com.mrsrik.ai_powered_commerce.order.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.mrsrik.ai_powered_commerce.order.dto.ProgressionResultDto;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;

/**
 * Implementation of OrderProgressionService.
 * Deliberately NOT @Transactional: stages execute in reverse order to ensure at most one step per order per run,
 * and each order transition executes in its own isolated transaction via OrderStepExecutor.
 */
@Service
@EnableConfigurationProperties(OrderProgressionProperties.class)
public class OrderProgressionServiceImpl implements OrderProgressionService {

    private static final Logger log = LoggerFactory.getLogger(OrderProgressionServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderStepExecutor stepExecutor;
    private final OrderProgressionProperties properties;
    private final Clock clock;

    public OrderProgressionServiceImpl(OrderRepository orderRepository,
                                       OrderStepExecutor stepExecutor,
                                       OrderProgressionProperties properties,
                                       Clock clock) {
        this.orderRepository = orderRepository;
        this.stepExecutor = stepExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ProgressionResultDto advanceAll() {
        int confirmed = 0;
        int shipped = 0;
        int delivered = 0;
        int failed = 0;

        Pageable batch = PageRequest.of(0, properties.progressionBatchSize());

        // Stage 1: SHIPPED -> DELIVERED (reverse order to ensure at most one step per order)
        Instant cutoff = Instant.now(clock).minus(properties.deliveryDelay());
        List<UUID> deliverableIds = orderRepository.findDeliverableIds(cutoff, batch);
        for (UUID orderId : deliverableIds) {
            try {
                StepOutcome outcome = stepExecutor.advanceOne(orderId, OrderStatus.SHIPPED);
                if (outcome == StepOutcome.ADVANCED) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to advance order {} from SHIPPED to DELIVERED", orderId, e);
                failed++;
            }
        }

        // Stage 2: CONFIRMED -> SHIPPED
        List<UUID> confirmableIds = orderRepository.findIdsByStatus(OrderStatus.CONFIRMED, batch);
        for (UUID orderId : confirmableIds) {
            try {
                StepOutcome outcome = stepExecutor.advanceOne(orderId, OrderStatus.CONFIRMED);
                if (outcome == StepOutcome.ADVANCED) {
                    shipped++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to advance order {} from CONFIRMED to SHIPPED", orderId, e);
                failed++;
            }
        }

        // Stage 3: PENDING -> CONFIRMED
        List<UUID> pendingIds = orderRepository.findIdsByStatus(OrderStatus.PENDING, batch);
        for (UUID orderId : pendingIds) {
            try {
                StepOutcome outcome = stepExecutor.advanceOne(orderId, OrderStatus.PENDING);
                if (outcome == StepOutcome.ADVANCED) {
                    confirmed++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to advance order {} from PENDING to CONFIRMED", orderId, e);
                failed++;
            }
        }

        return new ProgressionResultDto(confirmed, shipped, delivered, failed);
    }
}
