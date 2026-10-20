package com.example.vitalrelics.common.relics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the relic stat/property arithmetic.
 */
class CombatMathTest {

	@Test
	void propertyDefaultsAreNeutral() {
		final Relic.Properties.Info basic = Relic.Properties.Info.basic();

		assertEquals(0.0, basic.add, 1e-9);
		assertEquals(0.0, basic.mul_base, 1e-9);
		assertEquals(1.0, basic.mul_total, 1e-9);
	}

	@Test
	void computePropertiesIgnoresUnsetFields() {
		final Relic first = new Relic();
		final Relic.Properties.Info a = new Relic.Properties.Info();
		a.add = 3.0;
		first.properties.put("max_health", a);

		final Relic second = new Relic();
		final Relic.Properties.Info b = new Relic.Properties.Info();
		b.mul_base = 2.0;
		b.mul_total = 4.0;
		second.properties.put("max_health", b);

		final Relic.Properties.Info result =
				Loader.computeProperties(List.of(first, second)).get("max_health");

		assertNotNull(result);
		assertEquals(3.0, result.add, 1e-9);
		assertEquals(2.0, result.mul_base, 1e-9);
		assertEquals(4.0, result.mul_total, 1e-9);
	}

	@Test
	void computeTicksOnlyAggregatesEntriesDueOnTheCurrentTick() {
		final Relic first = new Relic();
		final Relic.Ticks.Info a = new Relic.Ticks.Info();
		a.interval_ticks = 5;
		a.add = 1.0;
		a.ratio_add = 0.1;
		first.ticks.put("regen", a);

		final Relic second = new Relic();
		final Relic.Ticks.Info b = new Relic.Ticks.Info();
		b.interval_ticks = 3;
		b.add = 2.0;
		b.ratio_add = 0.2;
		second.ticks.put("regen", b);

		final Map<String, Relic.Ticks.Info> atTen =
				Loader.computeTicks(List.of(first, second), 10);
		assertEquals(1.0, atTen.get("regen").add, 1e-9);
		assertEquals(0.1, atTen.get("regen").ratio_add, 1e-9);

		final Map<String, Relic.Ticks.Info> atFifteen =
				Loader.computeTicks(List.of(first, second), 15);
		assertEquals(3.0, atFifteen.get("regen").add, 1e-9);
		assertEquals(0.3, atFifteen.get("regen").ratio_add, 1e-9);

		assertTrue(Loader.computeTicks(List.of(first, second), 7).isEmpty());
	}

	@Test
	void applyCallbackAppliesAllFlatsBeforeAllModifiers() {
		final Relic first = new Relic();
		final Relic.Callbacks.Info a = new Relic.Callbacks.Info();
		a.flat = 1.0;
		first.callbacks.put("damage", a);

		final Relic second = new Relic();
		final Relic.Callbacks.Info b = new Relic.Callbacks.Info();
		b.flat = 2.0;
		b.modifier = 2.0;
		second.callbacks.put("damage", b);

		// Flat phase: 5 + 1 + 2 = 8. Modifier phase: 8 * 2 = 16.
		assertEquals(16.0, Loader.applyCallback(
				List.of(first, second), "damage", 5.0, 100.0), 1e-9);
	}

	@Test
	void applyCallbackNeverReturnsANegativeValue() {
		final Relic relic = new Relic();
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();
		callback.flat = -100.0;
		relic.callbacks.put("damage", callback);

		assertEquals(0.0, Loader.applyCallback(
				List.of(relic), "damage", 10.0, 100.0), 1e-9);
		assertEquals(0.0, Loader.applyCallback(
				List.of(), "damage", -5.0, 100.0), 1e-9);
	}

	@Test
	void callbackHelpersAreNoOpsWhenUnset() {
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();

		assertEquals(5.0, callback.applyFlat(5.0), 1e-9);
		assertEquals(5.0, callback.applyModifier(5.0), 1e-9);

		callback.flat = 2.0;
		callback.modifier = 3.0;

		assertEquals(7.0, callback.applyFlat(5.0), 1e-9);
		assertEquals(15.0, callback.applyModifier(5.0), 1e-9);
	}

	@Test
	void clampWithoutBoundsReturnsTheInput() {
		assertEquals(123.456,
				new Relic.Callbacks.Info().clamp(123.456, 100.0), 0.0);
	}

	@Test
	void clampWithContradictoryBoundsReturnsTheMinimum() {
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();
		callback.minimum = 10.0;
		callback.maximum = 5.0;

		assertEquals(10.0, callback.clamp(0.0, 100.0), 0.0);
	}

	@Test
	void clampStaysWithinAbsoluteBounds() {
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();
		callback.minimum = 10.0;
		callback.maximum = 20.0;

		for (final double value : new double[]{-1e9, -10.0, 0.0, 15.0, 1e9}) {
			final double clamped = callback.clamp(value, 0.0);

			assertTrue(clamped >= 10.0 && clamped <= 20.0,
					"value " + value + " escaped the bounds: " + clamped);
		}
	}

	@Test
	void clampStaysWithinRatioBounds() {
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();
		callback.ratio_minimum = 0.5;
		callback.ratio_maximum = 2.0;

		for (final double value : new double[]{-1e9, 0.0, 100.0, 1e9}) {
			final double clamped = callback.clamp(value, 100.0);

			assertTrue(clamped >= 50.0 && clamped <= 200.0,
					"value " + value + " escaped the ratio bounds: " + clamped);
		}
	}

	@Test
	void levelOfSuchPassiveSkillUsesTheHighestRelicLevel() {
		final Relic first = new Relic();
		first.passive_skills.put("thorns", 1.5);

		final Relic second = new Relic();
		second.passive_skills.put("thorns", 2.5);

		assertEquals(2.5, Loader.levelOfSuchPassiveSkill(
				List.of(first, second), "thorns"), 1e-9);
		assertEquals(0.0, Loader.levelOfSuchPassiveSkill(
				List.of(first, second), "flight"), 1e-9);
	}

	@Test
	void effectImmunityHandlesSpecificAndCatchAllEntries() {
		final Relic specific = new Relic();
		specific.immune_to_effects.add("wither");

		assertTrue(specific.isImmuneToEffect("wither", true));
		assertTrue(specific.isImmuneToEffect("wither", false));
		assertFalse(specific.isImmuneToEffect("slowness", true));

		final Relic allNegative = new Relic();
		allNegative.immune_to_effects.add("all_negative");

		assertTrue(allNegative.isImmuneToEffect("slowness", true));
		assertFalse(allNegative.isImmuneToEffect("slowness", false));

		assertTrue(Loader.isImmuneToEffect(
				List.of(specific, allNegative), "wither", false));
		assertFalse(Loader.isImmuneToEffect(
				List.of(specific, allNegative), "slowness", false));
	}
}
