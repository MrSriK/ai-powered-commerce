package com.mrsrik.ai_powered_commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates TransactionIdGenerator and Property 13:
 * Transaction ids are unique and well formed (Requirement 4.17).
 */
class TransactionIdGeneratorTest {

    private static final Pattern TXN_PATTERN = Pattern.compile("^TXN-\\d{8}-[0-9A-F]{12}$");

    @Test
    @DisplayName("Generates transaction ID with exact format and UTC date from clock")
    void generatesFormattedTransactionId() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T15:30:00Z"), ZoneOffset.UTC);
        TransactionIdGenerator generator = new TransactionIdGenerator(fixedClock);

        String txnId = generator.generate();

        assertThat(txnId).isNotNull();
        assertThat(txnId).matches(TXN_PATTERN);
        assertThat(txnId).startsWith("TXN-20261113-");
        assertThat(txnId).hasSize(25);
    }

    @Test
    @DisplayName("Property 13: Transaction IDs are all well-formed and unique across 10,000 iterations")
    void property13UniqueAndWellFormed() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-20T08:00:00Z"), ZoneOffset.UTC);
        TransactionIdGenerator generator = new TransactionIdGenerator(fixedClock);

        int count = 10_000;
        Set<String> generatedIds = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            assertThat(id).matches(TXN_PATTERN);
            assertThat(id).startsWith("TXN-20260520-");
            generatedIds.add(id);
        }

        assertThat(generatedIds).hasSize(count);
    }
}

