package com.example.vitalrelics.gametest;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.Loader;
import com.example.vitalrelics.common.relics.Relic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}.
 *
 * <p>{@link #relicsAreRegistered} inspects registry/data state; {@code chest_loot} and
 * {@code wither_loot} additionally exercise the acquisition path end to end by looting a chest
 * and killing a wither. All tests reference the tiny {@code vitalrelics:empty} structure shipped
 * by the common module, and avoid the class-name prefix so that the structure id stays stable.</p>
 */
@GameTestHolder(Manifest.MODID)
@PrefixGameTestTemplate(false)
public class VitalGameTests {

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void relicsAreRegistered(final GameTestHelper helper) {
		int checked = 0;

		for (final Relic relic : Loader.get().relics_) {
			final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Manifest.MODID, relic.id);

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

	/**
	 * Generates {@code minecraft:chests/simple_dungeon} into a real chest block entity with
	 * different loot seeds until the injected {@code common_relic_core} pool is seen. This
	 * exercises the plain (no biome filter) chest injection.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void chestLoot(final GameTestHelper helper) {
		final BlockPos pos = BlockPos.ZERO;

		helper.setBlock(pos, Blocks.CHEST);

		final ChestBlockEntity chest = helper.getBlockEntity(pos);
		final ResourceKey<LootTable> table = ResourceKey.create(
				Registries.LOOT_TABLE,
				ResourceLocation.fromNamespaceAndPath("minecraft", "chests/simple_dungeon")
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
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void witherLoot(final GameTestHelper helper) {
		final BlockPos center = BlockPos.ZERO;
		final ServerLevel level = helper.getLevel();
		final ResourceKey<Biome> cherryGrove = ResourceKey.create(
				Registries.BIOME,
				ResourceLocation.fromNamespaceAndPath("minecraft", "cherry_grove")
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
			helper.getEntities(EntityType.ITEM, center, 2.0)
					.forEach(entity -> entity.discard());

			final var wither = helper.spawn(EntityType.WITHER, center);
			wither.kill();

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
		return helper.getEntities(EntityType.ITEM, pos, distance)
				.stream()
				.anyMatch(entity -> entity.getItem().is(item));
	}

	private static Item relicItem(final String relicId) {
		final Item item = BuiltInRegistries.ITEM.get(
				ResourceLocation.fromNamespaceAndPath(Manifest.MODID, relicId));

		if (item == null)
			throw new IllegalStateException("missing relic item: " + relicId);

		return item;
	}
}
