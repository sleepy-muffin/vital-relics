package com.example.vitalrelics.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for arrow durability math.
 */
class ArrowDurabilityTest {

	@Test
	void rejectsNonPositiveAndNonFiniteDurability() {
		for (final double durability : new double[]{
				0.0, -1.0, Double.NaN,
				Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {

			assertThrows(IllegalArgumentException.class,
					() -> new ArrowDurability(durability),
					"durability " + durability + " must be rejected");
		}

		assertDoesNotThrow(() -> new ArrowDurability(0.5));
	}

	@Test
	void canBreakRequiresStrictlyMoreDurabilityThanHardness() {
		final ArrowDurability arrow = new ArrowDurability(10.0);

		assertTrue(arrow.canBreak(9.999));
		assertFalse(arrow.canBreak(10.0));
		assertFalse(arrow.canBreak(10.001));
	}

	@Test
	void canBreakRejectsInvalidHardness() {
		final ArrowDurability arrow = new ArrowDurability(10.0);

		assertFalse(arrow.canBreak(-1.0));
		assertFalse(arrow.canBreak(Double.NaN));
		assertFalse(arrow.canBreak(Double.POSITIVE_INFINITY));
		assertFalse(arrow.canBreak(Double.NEGATIVE_INFINITY));
		assertTrue(arrow.canBreak(0.0));
	}

	@Test
	void spendSubtractsAndRejectsInsufficientDurability() {
		final ArrowDurability arrow = new ArrowDurability(10.0);

		arrow.spend(3.0);
		assertEquals(7.0, arrow.remaining(), 1e-9);

		assertThrows(IllegalArgumentException.class, () -> arrow.spend(7.001));
		assertEquals(7.0, arrow.remaining(), 1e-9,
				"a failed spend must not change the remaining durability");
	}
}
