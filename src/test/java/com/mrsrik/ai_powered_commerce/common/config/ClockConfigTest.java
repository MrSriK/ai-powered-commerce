package com.mrsrik.ai_powered_commerce.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void clockUsesUtcZone() {
        Clock clock = new ClockConfig().clock();

        assertEquals(ZoneOffset.UTC, clock.getZone());
    }

    @Test
    void successiveInstantsDoNotDecrease() {
        Clock clock = new ClockConfig().clock();

        Instant first = clock.instant();
        Instant second = clock.instant();

        assertFalse(second.isBefore(first));
    }
}
