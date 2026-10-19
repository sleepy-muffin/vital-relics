package com.example.vitalrelics.common.relics;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the bundled recipe/loot configuration.
 *
 * <p>{@link Acquisition#load(Path)} is a singleton and may only be called once per JVM, so all of
 * the assertions below share a single load performed in {@link #load()}.</p>
 */
class AcquisitionTest {

	@BeforeAll
	static void load() throws IOException {
		final Path dir = Files.createTempDirectory("vitalrelics-acquisition-test");
		Acquisition.load(dir.resolve("recipes.json"));
	}

	@Test
	void cherryCrossDropsFromWitherInCherryGrove() {
		final List<Acquisition.Data.Loot> rules = Acquisition.get().data.loot.get("cherry_cross");

		assertNotNull(rules, "cherry_cross must have a loot entry");
		assertEquals(1, rules.size(), "cherry_cross must have exactly one loot rule");

		final Acquisition.Data.Loot rule = rules.get(0);
		assertEquals("minecraft:entities/wither", rule.table);
		assertEquals(0.5, rule.chance, 1e-9);
		assertEquals("minecraft:cherry_grove", rule.biome);
	}

	@Test
	void lootRulesWithoutBiomeAreAllowed() {
		final Acquisition.Data.Loot sample = Acquisition.get().data.loot.values().stream()
				.flatMap(List::stream)
				.filter(rule -> rule.biome == null)
				.findFirst()
				.orElse(null);

		assertNotNull(sample, "at least one bundled loot rule must be biome-independent");
		assertNull(sample.biome);
	}

	@Test
	void recipesAndLootAreParsed() {
		assertFalse(Acquisition.get().data.recipes.isEmpty(), "recipes must be parsed");
		assertFalse(Acquisition.get().data.loot.isEmpty(), "loot must be parsed");
	}
}
