package com.mrsrik.ai_powered_commerce.order.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Generates unique transaction IDs for orders (Requirement 4.17, D-38).
 * Format: TXN-&lt;yyyyMMdd&gt;-&lt;12 uppercase hexadecimal characters&gt;
 */
@Component
public class TransactionIdGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private final Clock clock;

    public TransactionIdGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        String datePart = DATE_FORMATTER.format(clock.instant());
        String hexPart = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return "TXN-" + datePart + "-" + hexPart;
    }
}

