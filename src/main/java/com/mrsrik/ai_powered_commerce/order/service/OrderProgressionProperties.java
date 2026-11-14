package com.mrsrik.ai_powered_commerce.order.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for automated order progression (Requirements 12.5, 12.6).
 *
 * @param deliveryDelay minimum time an order must remain in SHIPPED status before becoming eligible for DELIVERED
 * @param progressionBatchSize maximum number of order IDs to select and process per stage per invocation
 */
@ConfigurationProperties(prefix = "app.orders")
public record OrderProgressionProperties(
        @DefaultValue("PT5M") Duration deliveryDelay,
        @DefaultValue("100") int progressionBatchSize) {

    public OrderProgressionProperties {
        if (deliveryDelay == null) {
            deliveryDelay = Duration.ofMinutes(5);
        }
        if (progressionBatchSize <= 0) {
            progressionBatchSize = 100;
        }
    }
}
