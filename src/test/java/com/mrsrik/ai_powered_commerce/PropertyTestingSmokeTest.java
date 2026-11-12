package com.mrsrik.ai_powered_commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Smoke test for the project's "property-style" test technique.
 *
 * <p>
 * A property is checked with a JUnit Jupiter {@code @ParameterizedTest}. Its
 * {@code @MethodSource} generates a fixed number of cases from a
 * {@link java.util.Random} created with a constant seed, so every run sees the
 * same inputs.
 *
 * <p>
 * The seed is fixed on purpose: builds are reproducible and a failure is
 * never flaky. To explore new cases, change {@link #SEED} (or raise
 * {@link #CASES}) and run again. Keep the new value if it finds a bug.
 *
 * <p>
 * There is no shrinking. A failing case reports the seed and the exact
 * inputs in its message, so it can be reproduced and turned into a plain
 * example test.
 */
class PropertyTestingSmokeTest {

	private static final long SEED = 20240601L;
	private static final int CASES = 100;

	static Stream<Arguments> intPairs() {
		Random random = new Random(SEED);
		return IntStream.range(0, CASES)
				.mapToObj(i -> Arguments.of(random.nextInt(), random.nextInt()));
	}

	@ParameterizedTest(name = "a + b == b + a [{index}] a={0}, b={1}")
	@MethodSource("intPairs")
	void additionIsCommutative(int a, int b) {
		assertEquals(a + b, b + a, "seed=" + SEED + ", a=" + a + ", b=" + b);
	}

	@Test
	void jupiterEngineRuns() {
		assertEquals(4, 2 + 2);
	}
}
