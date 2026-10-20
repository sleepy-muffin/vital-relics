package com.example.vitalrelics.common;

import com.example.vitalrelics.common.relics.Relic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for spell parameter parsing, scoring and selection.
 */
class RelicSpellsTest {

	private static Relic.Spells.Info spell(final Object... pairs) {
		final Relic.Spells.Info info = new Relic.Spells.Info();

		for (int i = 0; i < pairs.length; i += 2)
			info.parameters.put((String) pairs[i], pairs[i + 1]);

		return info;
	}

	@Test
	void numberParameterReadsNumbersAndFallsBack() {
		assertEquals(3.0, RelicSpells.numberParameter(
				spell("intensity", 3), "intensity", -1.0), 1e-9);
		assertEquals(-1.0, RelicSpells.numberParameter(
				spell(), "intensity", -1.0), 1e-9);
		assertEquals(-1.0, RelicSpells.numberParameter(
				spell("intensity", "high"), "intensity", -1.0), 1e-9);
	}

	@Test
	void cooldownTicksInvertsRecovery() {
		assertEquals(20, RelicSpells.cooldownTicks(spell("recovery", 1.0)));
		assertEquals(5, RelicSpells.cooldownTicks(spell("recovery", 4.0)));
		assertEquals(1, RelicSpells.cooldownTicks(spell("recovery", 100.0)));
	}

	@Test
	void cooldownTicksIsClampedToTheMaximum() {
		assertEquals(1200, RelicSpells.cooldownTicks(spell("recovery", 0.001)));
	}

	@Test
	void cooldownTicksRejectsInvalidRecovery() {
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell()));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.NaN)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", 0.0)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", -1.0)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.POSITIVE_INFINITY)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", Double.NEGATIVE_INFINITY)));
		assertThrows(IllegalArgumentException.class,
				() -> RelicSpells.cooldownTicks(spell("recovery", "fast")));
	}

	@Test
	void explicitPriorityWinsOverIntensityTimesRecovery() {
		final Relic.Spells.Info base =
				spell("intensity", 100.0, "recovery", 1.0, "priority", 10);
		final Relic.Spells.Info candidate =
				spell("intensity", 1.0, "recovery", 1.0, "priority", 20);

		assertTrue(RelicSpells.isTheSpellBetter(base, candidate));
		assertFalse(RelicSpells.isTheSpellBetter(candidate, base));
	}

	@Test
	void equalScoresAreNotBetter() {
		final Relic.Spells.Info base = spell("intensity", 2.0, "recovery", 3.0);
		final Relic.Spells.Info candidate = spell("intensity", 3.0, "recovery", 2.0);

		assertFalse(RelicSpells.isTheSpellBetter(base, candidate));
	}

	@Test
	void nonNumericPriorityFallsBackToTheDefaultScore() {
		final Relic.Spells.Info base =
				spell("intensity", 2.0, "recovery", 3.0, "priority", "high");
		final Relic.Spells.Info candidate =
				spell("intensity", 3.0, "recovery", 3.0, "priority", "low");

		assertTrue(RelicSpells.isTheSpellBetter(base, candidate));
		assertFalse(RelicSpells.isTheSpellBetter(candidate, base));
	}

	@Test
	void gatherSpellsKeepsTheBestEntryAndInsertionOrder() {
		final Relic first = new Relic();
		first.available_spells.put("a", spell("priority", 1));
		first.available_spells.put("b", spell("priority", 5));

		final Relic second = new Relic();
		second.available_spells.put("b", spell("priority", 9));
		second.available_spells.put("c", spell("priority", 2));

		final Map<String, Relic.Spells.Info> result =
				RelicSpells.gatherSpells(List.of(first, second));

		assertEquals(List.of("a", "b", "c"), List.copyOf(result.keySet()));
		assertSame(second.available_spells.get("b"), result.get("b"));
	}

	@Test
	void gatherSpellsKeepsAnEarlierStrongerEntry() {
		final Relic first = new Relic();
		first.available_spells.put("a", spell("priority", 1));

		final Relic second = new Relic();
		second.available_spells.put("a", spell("priority", 0));

		final Map<String, Relic.Spells.Info> result =
				RelicSpells.gatherSpells(List.of(first, second));

		assertSame(first.available_spells.get("a"), result.get("a"));
	}
}
