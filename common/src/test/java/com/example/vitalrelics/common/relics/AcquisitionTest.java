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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void everyLootRuleIsWellFormed() {
		for (final var entry : Acquisition.get().data.loot.entrySet()) {
			assertFalse(entry.getValue().isEmpty(),
					entry.getKey() + " must have loot rules");

			for (final Acquisition.Data.Loot rule : entry.getValue()) {
				assertNotNull(rule.table, entry.getKey() + " rule table");
				assertFalse(rule.table.isBlank(), entry.getKey() + " rule table");
				assertTrue(rule.chance >= 0.0 && rule.chance <= 1.0,
						entry.getKey() + " chance: " + rule.chance);

				if (rule.biome != null)
					assertFalse(rule.biome.isBlank(), entry.getKey() + " biome");
			}
		}
	}

	@Test
	void everyCraftingRecipeIsNormalized() {
		for (final var entry : Acquisition.get().data.recipes.entrySet()) {
			final Acquisition.Data.Crafting recipe = entry.getValue();
			final String id = entry.getKey();

			assertNotNull(recipe.type, id + " type");
			assertTrue(recipe.count > 0, id + " count");

			if (!"shaped".equals(recipe.type)) {
				assertEquals("shapeless", recipe.type, id + " type");
				assertFalse(recipe.ingredients.isEmpty(), id + " ingredients");
				assertTrue(recipe.pattern.isEmpty(), id + " pattern");
				continue;
			}

			assertFalse(recipe.pattern.isEmpty(), id + " pattern");

			final int width = recipe.pattern.get(0).length();
			assertTrue(width > 0, id + " pattern width");

			for (final String row : recipe.pattern)
				assertEquals(width, row.length(), id + " row width");

			assertFalse(recipe.pattern.get(0).isBlank(), id + " first row");
			assertFalse(recipe.pattern.get(recipe.pattern.size() - 1).isBlank(),
					id + " last row");

			boolean firstColumn = false;
			boolean lastColumn = false;

			for (final String row : recipe.pattern) {
				firstColumn |= row.charAt(0) != ' ';
				lastColumn |= row.charAt(width - 1) != ' ';
			}

			assertTrue(firstColumn, id + " first column is unused");
			assertTrue(lastColumn, id + " last column is unused");

			for (final String symbol : recipe.key.keySet())
				assertEquals(1, symbol.length(), id + " key symbol");
		}
	}
}
