package com.example.vitalrelics.common.relics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the pure (loader-independent) helpers of {@link Loader}.
 *
 * <p>These methods never touch the singleton instance, so they can be exercised freely.</p>
 */
class LoaderHelpersTest {

	@Test
	void addListAppendsStringsAndRejectsNonStrings() {
		final List<String> target = new ArrayList<>();
		Loader.add_list(target, List.of("a", "b"));
		assertEquals(List.of("a", "b"), target);

		Loader.add_list(target, "not-a-list"); // not a list: ignored
		assertEquals(List.of("a", "b"), target);

		assertThrows(IllegalArgumentException.class,
				() -> Loader.add_list(new ArrayList<>(), List.of(1)));
	}

	@Test
	void addMapIntParsesNumbers() {
		final Map<String, Integer> target = new LinkedHashMap<>();
		final Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("strength", 3);
		raw.put("speed", 2.0);

		Loader.add_map_int(target, raw);

		assertEquals(3, target.get("strength"));
		assertEquals(2, target.get("speed"));
	}

	@Test
	void addMapDoubleEnforcesBounds() {
		final Map<String, Double> target = new LinkedHashMap<>();
		final Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("chance", 0.5);

		Loader.add_map_double(target, raw, 0.0, 1.0);
		assertEquals(0.5, target.get("chance"), 1e-9);

		assertThrows(IllegalArgumentException.class,
				() -> Loader.add_map_double(new LinkedHashMap<>(), raw, 0.0, 0.4));
	}

	@Test
	void computePropertiesAccumulatesRelics() {
		final Relic first = new Relic();
		final Relic.Properties.Info a = new Relic.Properties.Info();
		a.add = 2.0;
		a.mul_base = 1.0;
		a.mul_total = 1.0;
		first.properties.put("max_health", a);

		final Relic second = new Relic();
		final Relic.Properties.Info b = new Relic.Properties.Info();
		b.add = 3.0;
		b.mul_base = 0.5;
		b.mul_total = 1.5;
		second.properties.put("max_health", b);

		final Map<String, Relic.Properties.Info> total =
				Loader.computeProperties(List.of(first, second));

		final Relic.Properties.Info result = total.get("max_health");
		assertNotNull(result);
		assertEquals(5.0, result.add, 1e-9);
		assertEquals(1.5, result.mul_base, 1e-9);
		assertEquals(1.5, result.mul_total, 1e-9);
	}

	@Test
	void applyCallbackAppliesFlatThenModifier() {
		final Relic relic = new Relic();
		final Relic.Callbacks.Info callback = new Relic.Callbacks.Info();
		callback.flat = 2.0;
		callback.modifier = 3.0;
		relic.callbacks.put("damage", callback);

		// (5 + 2) * 3 = 21
		assertEquals(21.0, Loader.applyCallback(List.of(relic), "damage", 5.0, 100.0), 1e-9);
	}
}
