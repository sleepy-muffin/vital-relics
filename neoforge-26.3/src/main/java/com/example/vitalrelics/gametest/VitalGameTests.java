package com.example.vitalrelics.gametest;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.Loader;
import com.example.vitalrelics.common.relics.Relic;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Since NeoForge 1.21.8 the {@code @GameTest} annotations are gone: tests are registered on the
 * mod event bus instead. Most tests inspect registry/data state; {@code chest_loot} and
 * {@code wither_loot} additionally exercise the acquisition path end to end by looting a chest
 * and killing a wither. All tests reference the tiny {@code vitalrelics:empty} structure shipped
 * by the common module.</p>
 *
 * <p>The test bodies live in the {@code minecraft:test_function} registry and are wrapped in
 * vanilla's {@link FunctionGameTestInstance}. This matters because the {@code test_instance}
 * registry is synchronized to clients: a custom {@code GameTestInstance} subclass would need its
 * own registered {@code test_instance_type} and otherwise fails to serialize when a client joins.</p>
 */
public final class VitalGameTests {
	private VitalGameTests() {}

	private static final Identifier SMOKE = id("smoke");
	private static final Identifier LOOT = id("loot");
	private static final Identifier SYNCABLE = id("syncable");
	private static final Identifier CHEST_LOOT = id("chest_loot");
	private static final Identifier WITHER_LOOT = id("wither_loot");

	public static void registerFunctions(final RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, SMOKE, () -> VitalGameTests::smoke);
		event.register(Registries.TEST_FUNCTION, LOOT, () -> VitalGameTests::lootIsWellFormed);
		event.register(Registries.TEST_FUNCTION, SYNCABLE, () -> VitalGameTests::testInstancesAreSyncable);
		event.register(Registries.TEST_FUNCTION, CHEST_LOOT, () -> VitalGameTests::chestLoot);
		event.register(Registries.TEST_FUNCTION, WITHER_LOOT, () -> VitalGameTests::witherLoot);
	}

	public static void registerTests(final RegisterGameTestsEvent event) {
		final Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
				id("default"),
				new TestEnvironmentDefinition.AllOf()
		);

		event.registerTest(SMOKE, test(SMOKE, environment));
		event.registerTest(LOOT, test(LOOT, environment));
		event.registerTest(SYNCABLE, test(SYNCABLE, environment));
		event.registerTest(CHEST_LOOT, test(CHEST_LOOT, environment));
		event.registerTest(WITHER_LOOT, test(WITHER_LOOT, environment));
	}

	private static FunctionGameTestInstance test(
			final Identifier id,
			final Holder<TestEnvironmentDefinition<?>> environment) {
		return new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, id),
				new TestData<>(environment, id("empty"), 200, 1, true)
		);
	}

	private static void smoke(final GameTestHelper helper) {
		int checked = 0;

		for (final Relic relic : Loader.get().relics_) {
			final Identifier id = id(relic.id);

			helper.assertTrue(
					BuiltInRegistries.ITEM.containsKey(id),
					"missing relic item: " + relic.id
			);

			checked++;
		}

		helper.assertTrue(checked > 0, "no relic items were registered");

		final var rules = Acquisition.get().data.loot.get("cherry_cross");
		helper.assertTrue(rules != null && !rules.isEmpty(), "cherry_cross loot entry is missing");

		helper.succeed();
	}

	private static void lootIsWellFormed(final GameTestHelper helper) {
		final var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);

		for (final var entry : Acquisition.get().data.loot.entrySet()) {
			final Identifier item = id(entry.getKey());

			helper.assertTrue(
					BuiltInRegistries.ITEM.containsKey(item),
					"loot entry has no registered item: " + item
			);
			helper.assertTrue(
					!entry.getValue().isEmpty(),
					"loot entry has no rules: " + entry.getKey()
			);

			for (final var rule : entry.getValue()) {
				helper.assertTrue(
						Identifier.tryParse(rule.table) != null,
						"invalid loot table id '" + rule.table + "' for " + entry.getKey()
				);
				helper.assertTrue(
						rule.chance >= 0.0 && rule.chance <= 1.0,
						"loot chance out of range: " + rule.chance + " for " + entry.getKey()
				);

				if (rule.biome == null)
					continue;

				final Identifier biome = Identifier.tryParse(rule.biome);

				helper.assertTrue(
						biome != null && biomes.get(
								ResourceKey.create(Registries.BIOME, biome)).isPresent(),
						"unknown loot biome '" + rule.biome + "' for " + entry.getKey()
				);
			}
		}

		helper.succeed();
	}

	/**
	 * Generates {@code minecraft:chests/simple_dungeon} into a real chest block entity with
	 * different loot seeds until the injected {@code common_relic_core} pool is seen. This
	 * exercises the plain (no biome filter) chest injection.
	 */
	private static void chestLoot(final GameTestHelper helper) {
		final BlockPos pos = BlockPos.ZERO;

		helper.setBlock(pos, Blocks.CHEST);

		final ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
		final ResourceKey<LootTable> table = ResourceKey.create(
				Registries.LOOT_TABLE,
				Identifier.withDefaultNamespace("chests/simple_dungeon")
		);
		final Item expected = relicItem("common_relic_core");

		boolean found = false;

		// common_relic_core has a 25% chance per roll, so a handful of seeds is enough.
		for (long seed = 1; seed <= 128 && !found; seed++) {
			chest.clearContent();
			chest.setLootTable(table);
			chest.setLootTableSeed(seed);
			chest.unpackLootTable(null);

			for (int slot = 0; slot < chest.getContainerSize() && !found; slot++)
				found = chest.getItem(slot).is(expected);
		}

		helper.assertTrue(
				found,
				"common_relic_core never appeared in 128 seeded simple_dungeon chests; "
						+ "the loot injection is not reaching the chest loot table"
		);

		helper.succeed();
	}

	/**
	 * Kills a real wither inside a cherry grove. Entity loot tables are biome-filtered here, so
	 * this covers both the death-drop path and the injected {@code LocationCheck}. The
	 * configured chance is 50%, hence the retries.
	 */
	private static void witherLoot(final GameTestHelper helper) {
		final BlockPos center = BlockPos.ZERO;
		final ServerLevel level = helper.getLevel();
		final ResourceKey<Biome> cherryGrove = ResourceKey.create(
				Registries.BIOME,
				Identifier.withDefaultNamespace("cherry_grove")
		);
		final BlockPos absolute = helper.absolutePos(center);

		ensureCherryGrove(level, absolute, cherryGrove);

		helper.assertTrue(
				level.getBiome(absolute).is(cherryGrove),
				"the test area is not a cherry grove"
		);

		final Item expected = relicItem("cherry_cross");

		// 0.5^24 is 1/16777216, so this is stable without needing a deterministic seed.
		for (int attempt = 0; attempt < 24; attempt++) {
			helper.despawnItem(center, 2.0);

			final var wither = helper.spawn(EntityTypes.WITHER, center);
			helper.kill(wither);

			if (hasItem(helper, expected, center, 2.0))
				break;
		}

		// Drops are added to the level on death; let them become visible on the next tick
		// instead of depending on same-tick entity visibility.
		helper.succeedWhen(
				() -> helper.assertItemEntityPresent(expected, center, 2.0)
		);
	}

	/**
	 * Rewrites the biomes around the test position.
	 * {@link net.minecraft.world.level.biome.BiomeManager} picks the biome from one of the eight
	 * nearest quads, so filling only the target quad is not enough: the whole 3x3x3 quad
	 * neighbourhood must be cherry grove.
	 */
	private static void ensureCherryGrove(
			final ServerLevel level,
			final BlockPos center,
			final ResourceKey<Biome> cherryGrove) {
		final BlockPos from = center.offset(-8, -8, -8);
		final BlockPos to = center.offset(8, 8, 8);

		// FillBiomeCommand bails out if any chunk in the region is not loaded.
		for (int chunkX = SectionPos.blockToSectionCoord(from.getX());
				chunkX <= SectionPos.blockToSectionCoord(to.getX());
				chunkX++) {
			for (int chunkZ = SectionPos.blockToSectionCoord(from.getZ());
					chunkZ <= SectionPos.blockToSectionCoord(to.getZ());
					chunkZ++) {
				level.getChunk(chunkX, chunkZ);
			}
		}

		FillBiomeCommand.fill(
				level,
				from,
				to,
				level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(cherryGrove)
		);
	}

	private static boolean hasItem(
			final GameTestHelper helper,
			final Item item,
			final BlockPos pos,
			final double distance) {
		return helper.getEntities(EntityTypes.ITEM, pos, distance)
				.stream()
				.anyMatch(entity -> entity.getItem().is(item));
	}

	private static Item relicItem(final String relicId) {
		return BuiltInRegistries.ITEM.get(id(relicId))
				.orElseThrow(() -> new IllegalStateException("missing relic item: " + relicId))
				.value();
	}

	/**
	 * Guards the exact failure that used to break client logins: every test instance must be
	 * encodable, because the whole {@code test_instance} registry is sent to joining clients.
	 */
	private static void testInstancesAreSyncable(final GameTestHelper helper) {
		final var access = helper.getLevel().registryAccess();
		final var ops = RegistryOps.create(NbtOps.INSTANCE, access);

		final var tests = access.lookupOrThrow(Registries.TEST_INSTANCE);
		tests.listElements().forEach(holder -> assertSyncable(
				helper,
				"test instance",
				holder.key().identifier(),
				GameTestInstance.DIRECT_CODEC.encodeStart(ops, holder.value())
		));

		final var environments = access.lookupOrThrow(Registries.TEST_ENVIRONMENT);
		environments.listElements().forEach(holder -> assertSyncable(
				helper,
				"test environment",
				holder.key().identifier(),
				TestEnvironmentDefinition.DIRECT_CODEC.encodeStart(ops, holder.value())
		));

		helper.succeed();
	}

	private static void assertSyncable(
			final GameTestHelper helper,
			final String kind,
			final Identifier id,
			final DataResult<?> encoded) {
		helper.assertTrue(
				encoded.result().isPresent(),
				kind + " is not serializable: " + id
						+ " -> "
						+ encoded.error()
								.map(error -> error.message())
								.orElse("unknown error")
		);
	}

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Manifest.MODID, path);
	}
}
