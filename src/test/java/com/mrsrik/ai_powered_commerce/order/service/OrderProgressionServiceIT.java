package com.mrsrik.ai_powered_commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.mrsrik.ai_powered_commerce.order.dto.ProgressionResultDto;
import com.mrsrik.ai_powered_commerce.order.entity.Order;
import com.mrsrik.ai_powered_commerce.order.enums.OrderStatus;
import com.mrsrik.ai_powered_commerce.order.repository.OrderRepository;
import com.mrsrik.ai_powered_commerce.support.AbstractPostgresIT;
import com.mrsrik.ai_powered_commerce.user.entity.User;

/**
 * Integration tests for automated order progression (Requirements 12.2 to 12.9, 4.23):
 * 1. Empty database returns all zeros.
 * 2. An order advances at most one status per invocation: PENDING -> CONFIRMED.
 * 3. Second run advances CONFIRMED -> SHIPPED and sets shippedAt.
 * 4. Delivery delay eligibility: SHIPPED order younger than delay (5m) is untouched; older is DELIVERED.
 * 5. Terminal orders (CANCELLED, DELIVERED) are never advanced or touched.
 * 6. Reverse-stage execution advances multiple orders across stages in one run without double-advancing any order.
 * 7. Status changed before locking is skipped without error and not counted in failures.
 * 8. Failure on one order is isolated and counted in failed while other eligible orders advance.
 */
class OrderProgressionServiceIT extends AbstractPostgresIT {

    @Autowired
    private OrderProgressionService progressionService;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoSpyBean
    private OrderStepExecutor stepExecutor;

    @Test
    @DisplayName("Empty database returns all zeros")
    void emptyDatabaseReturnsAllZeros() {
        ProgressionResultDto result = progressionService.advanceAll();

        assertThat(result.confirmed()).isZero();
        assertThat(result.shipped()).isZero();
        assertThat(result.delivered()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("Order advances at most one status per invocation: PENDING -> CONFIRMED only")
    void advancesAtMostOneStatusPerRun() {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.PENDING);

        ProgressionResultDto result = progressionService.advanceAll();

        assertThat(result).isEqualTo(new ProgressionResultDto(1, 0, 0, 0));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloaded.getShippedAt()).isNull();
    }

    @Test
    @DisplayName("Second run advances CONFIRMED -> SHIPPED and populates shippedAt")
    void secondRunAdvancesConfirmedToShipped() {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.CONFIRMED);

        ProgressionResultDto result = progressionService.advanceAll();

        assertThat(result).isEqualTo(new ProgressionResultDto(0, 1, 0, 0));

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(reloaded.getShippedAt()).isNotNull();
    }

    @Test
    @DisplayName("SHIPPED order is only delivered once deliveryDelay (5 min) has elapsed")
    void deliveryDelayEligibility() {
        User user = testData.user();
        Instant now = Instant.now();

        // Order shipped 4 minutes and 50 seconds ago (not yet eligible for delivery)
        Instant tooRecent = now.minus(Duration.ofMinutes(4).plusSeconds(50));
        Order recentOrder = testData.order(user.getId(), OrderStatus.SHIPPED, b -> b.shippedAt(tooRecent));

        ProgressionResultDto result1 = progressionService.advanceAll();
        assertThat(result1).isEqualTo(new ProgressionResultDto(0, 0, 0, 0));
        assertThat(orderRepository.findById(recentOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);

        // Update shippedAt to 5 minutes and 10 seconds ago (now eligible)
        Instant eligibleTime = now.minus(Duration.ofMinutes(5).plusSeconds(10));
        recentOrder.setShippedAt(eligibleTime);
        orderRepository.save(recentOrder);

        ProgressionResultDto result2 = progressionService.advanceAll();
        assertThat(result2).isEqualTo(new ProgressionResultDto(0, 0, 1, 0));
        assertThat(orderRepository.findById(recentOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Terminal orders (DELIVERED and CANCELLED) are never touched")
    void terminalOrdersNeverTouched() {
        User user = testData.user();
        Instant past = Instant.now().minus(Duration.ofHours(1));

        Order deliveredOrder = testData.order(user.getId(), OrderStatus.DELIVERED, b -> b.shippedAt(past));
        Order cancelledOrder = testData.order(user.getId(), OrderStatus.CANCELLED);

        ProgressionResultDto result = progressionService.advanceAll();
        assertThat(result).isEqualTo(new ProgressionResultDto(0, 0, 0, 0));

        assertThat(orderRepository.findById(deliveredOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderRepository.findById(cancelledOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Multiple orders across stages all advance exactly one step in a single run")
    void multipleOrdersAdvanceOneStepEach() {
        User user = testData.user();
        Instant eligibleCutoff = Instant.now().minus(Duration.ofMinutes(6));

        Order pendingOrder = testData.order(user.getId(), OrderStatus.PENDING);
        Order confirmedOrder = testData.order(user.getId(), OrderStatus.CONFIRMED);
        Order shippedOrder = testData.order(user.getId(), OrderStatus.SHIPPED, b -> b.shippedAt(eligibleCutoff));

        ProgressionResultDto result = progressionService.advanceAll();

        assertThat(result).isEqualTo(new ProgressionResultDto(1, 1, 1, 0));

        assertThat(orderRepository.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orderRepository.findById(confirmedOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderRepository.findById(shippedOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Stale orders whose status changed before locking are skipped without error")
    void staleOrdersAreSkipped() {
        User user = testData.user();
        Order order = testData.order(user.getId(), OrderStatus.CONFIRMED);

        // Expecting PENDING, but order is actually CONFIRMED
        StepOutcome outcome = stepExecutor.advanceOne(order.getId(), OrderStatus.PENDING);

        assertThat(outcome).isEqualTo(StepOutcome.SKIPPED);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Failure on one order is isolated and counted in failed while other orders advance")
    void failureIsolationDoesNotBlockOthers() {
        User user = testData.user();
        Order failingOrder = testData.order(user.getId(), OrderStatus.PENDING);
        Order goodOrder = testData.order(user.getId(), OrderStatus.PENDING);

        // Inject exception on advanceOne for failingOrder
        doThrow(new RuntimeException("Simulated DB lock failure"))
                .when(stepExecutor).advanceOne(eq(failingOrder.getId()), any());

        ProgressionResultDto result = progressionService.advanceAll();

        // 1 confirmed (goodOrder), 1 failed (failingOrder)
        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);

        assertThat(orderRepository.findById(failingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(orderRepository.findById(goodOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
