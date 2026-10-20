package com.example.vitalrelics.common.relics;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for raw recipe/loot parsing.
 *
 * <p>{@link Acquisition#parseInto(Acquisition.Data, Map)} is package-private and
 * never touches the singleton, so every test can parse its own in-memory root.</p>
 */
class AcquisitionParsingTest {

	private static Map<String, Object> map(final Object... pairs) {
		final Map<String, Object> result = new LinkedHashMap<>();

		for (int i = 0; i < pairs.length; i += 2)
			result.put((String) pairs[i], pairs[i + 1]);

		return result;
	}

	private static Map<String, Object> recipe(
			final String type,
			final Object... pairs) {

		final Map<String, Object> result = map(pairs);
		result.put("type", type);
		return result;
	}

	private static Acquisition.Data parse(final Map<String, Object> root) {
		final Acquisition.Data data = new Acquisition.Data();
		Acquisition.parseInto(data, root);
		return data;
	}

	@Test
	void shapedRecipePatternIsTrimmedAndKeyParsed() {
		final Map<String, Object> shaped = recipe(
				"shaped",
				"pattern", List.of("   ", "  A ", "  AA", "   "),
				"key", map("A", "minecraft:stick"),
				"count", 2
		);

		final Acquisition.Data data = parse(map(
				"recipes", map("test_relic", shaped)));

		final Acquisition.Data.Crafting parsed = data.recipes.get("test_relic");

		assertNotNull(parsed);
		assertEquals("shaped", parsed.type);
		assertEquals(List.of("A ", "AA"), parsed.pattern);
		assertEquals(map("A", "minecraft:stick"), parsed.key);
		assertEquals(2, parsed.count);
		assertTrue(parsed.ingredients.isEmpty());
	}

	@Test
	void shapedRecipeDefaultsToCountOneAndNoKey() {
		final Acquisition.Data data = parse(map(
				"recipes", map("test_relic", recipe(
						"shaped", "pattern", List.of("A")))));

		final Acquisition.Data.Crafting parsed = data.recipes.get("test_relic");

		assertNotNull(parsed);
		assertEquals(1, parsed.count);
		assertTrue(parsed.key.isEmpty());
		assertTrue(parsed.ingredients.isEmpty());
	}

	@Test
	void shapelessRecipeParsesIngredientsAndCount() {
		final Acquisition.Data data = parse(map(
				"recipes", map("test_relic", recipe(
						"shapeless",
						"ingredients", List.of("minecraft:stick", "minecraft:iron_ingot"),
						"count", 3))));

		final Acquisition.Data.Crafting parsed = data.recipes.get("test_relic");

		assertNotNull(parsed);
		assertEquals("shapeless", parsed.type);
		assertEquals(List.of("minecraft:stick", "minecraft:iron_ingot"),
				parsed.ingredients);
		assertEquals(3, parsed.count);
		assertTrue(parsed.pattern.isEmpty());
		assertTrue(parsed.key.isEmpty());
	}

	@Test
	void missingSectionsProduceEmptyData() {
		final Acquisition.Data data = parse(map());

		assertTrue(data.recipes.isEmpty());
		assertTrue(data.loot.isEmpty());
		assertTrue(data.undefined.isEmpty());
	}

	@Test
	void parseIntoClearsPreviouslyLoadedData() {
		final Acquisition.Data data = new Acquisition.Data();
		data.recipes.put("old", new Acquisition.Data.Crafting());
		data.loot.put("old", List.of(new Acquisition.Data.Loot()));
		data.undefined.add("old");

		Acquisition.parseInto(data, map());

		assertTrue(data.recipes.isEmpty());
		assertTrue(data.loot.isEmpty());
		assertTrue(data.undefined.isEmpty());
	}

	@Test
	void recipesSectionMustBeAnObject() {
		final IllegalArgumentException error = assertThrows(
				IllegalArgumentException.class,
				() -> parse(map("recipes", List.of())));

		assertEquals("'recipes' must be an object", error.getMessage());
	}

	@Test
	void eachRecipeMustBeAnObject() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", "not-an-object"))));
	}

	@Test
	void recipeRequiresAType() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", map("count", 1)))));
	}

	@Test
	void unsupportedRecipeTypeIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic",
						map("type", "smelting")))));
	}

	@Test
	void nonPositiveRecipeCountIsRejected() {
		for (final int count : new int[]{0, -1}) {
			assertThrows(IllegalArgumentException.class,
					() -> parse(map("recipes", map("test_relic",
							map("type", "shapeless", "count", count)))));
		}
	}

	@Test
	void shapedKeySymbolsMustBeOneCharacter() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", recipe(
						"shaped",
						"pattern", List.of("A"),
						"key", map("AB", "minecraft:stick"))))));
	}

	@Test
	void shapedKeyValuesMustBeStrings() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", recipe(
						"shaped",
						"pattern", List.of("A"),
						"key", map("A", 5))))));
	}

	@Test
	void shapedPatternCannotBeEmpty() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", recipe(
						"shaped", "pattern", List.of(" ", "  "))))));
	}

	@Test
	void shapedPatternMustBeAnArrayOfStrings() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", recipe(
						"shaped", "pattern", "A")))));

		assertThrows(IllegalArgumentException.class,
				() -> parse(map("recipes", map("test_relic", recipe(
						"shaped", "pattern", List.of(1))))));
	}

	@Test
	void lootRuleParsesTableChanceAndBiome() {
		final Acquisition.Data data = parse(map(
				"loot", map("cherry_cross", List.of(map(
						"table", "minecraft:entities/wither",
						"chance", 0.5,
						"biome", "minecraft:cherry_grove")))));

		final List<Acquisition.Data.Loot> rules = data.loot.get("cherry_cross");

		assertNotNull(rules);
		assertEquals(1, rules.size());
		assertEquals("minecraft:entities/wither", rules.get(0).table);
		assertEquals(0.5, rules.get(0).chance, 1e-9);
		assertEquals("minecraft:cherry_grove", rules.get(0).biome);
	}

	@Test
	void lootRuleBiomeIsOptional() {
		final Acquisition.Data data = parse(map(
				"loot", map("test_relic", List.of(map(
						"table", "minecraft:chests/simple_dungeon",
						"chance", 0.25)))));

		assertNull(data.loot.get("test_relic").get(0).biome);
	}

	@Test
	void lootChanceAcceptsBothBoundaries() {
		final Acquisition.Data data = parse(map(
				"loot", map("test_relic", List.of(
						map("table", "a:b", "chance", 0),
						map("table", "c:d", "chance", 1)))));

		assertEquals(0.0, data.loot.get("test_relic").get(0).chance, 1e-9);
		assertEquals(1.0, data.loot.get("test_relic").get(1).chance, 1e-9);
	}

	@Test
	void lootChanceOutsideZeroToOneIsRejected() {
		for (final double chance : new double[]{-0.0001, 1.0001}) {
			assertThrows(IllegalArgumentException.class,
					() -> parse(map("loot", map("test_relic", List.of(
							map("table", "a:b", "chance", chance))))));
		}
	}

	@Test
	void lootRuleRequiresTableAndChance() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", map("test_relic", List.of(
						map("chance", 0.5))))));

		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", map("test_relic", List.of(
						map("table", "a:b"))))));
	}

	@Test
	void lootBiomeMustNotBeBlank() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", map("test_relic", List.of(map(
						"table", "a:b",
						"chance", 0.5,
						"biome", "  "))))));
	}

	@Test
	void malformedLootContainersAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", List.of())));

		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", map("test_relic", "nope"))));

		assertThrows(IllegalArgumentException.class,
				() -> parse(map("loot", map("test_relic", List.of("nope")))));
	}

	@Test
	void lootRulesKeepTheirConfiguredOrder() {
		final Acquisition.Data data = parse(map(
				"loot", map("test_relic", List.of(
						map("table", "a:b", "chance", 0.1),
						map("table", "c:d", "chance", 0.9)))));

		assertEquals("a:b", data.loot.get("test_relic").get(0).table);
		assertEquals("c:d", data.loot.get("test_relic").get(1).table);
	}

	@Test
	void undefinedListIsParsed() {
		final Acquisition.Data data = parse(map(
				"undefined", List.of("a", "b")));

		assertEquals(List.of("a", "b"), data.undefined);
	}

	@Test
	void malformedUndefinedListIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> parse(map("undefined", "a")));

		assertThrows(IllegalArgumentException.class,
				() -> parse(map("undefined", List.of(1))));
	}
}
