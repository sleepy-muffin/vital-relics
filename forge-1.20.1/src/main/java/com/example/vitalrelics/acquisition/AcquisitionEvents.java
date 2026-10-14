package com.example.vitalrelics.acquisition;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class AcquisitionEvents {
	private AcquisitionEvents() {}

	@SubscribeEvent
	public static void onLootTableLoad(final LootTableLoadEvent event) {
		final String table = event.getName().toString();

		for (final var entry : Acquisition.get().data.loot.entrySet()) {
			final ResourceLocation id =
					new ResourceLocation(Manifest.MODID, entry.getKey());

			if (!BuiltInRegistries.ITEM.containsKey(id))
				continue;

			final Item item = BuiltInRegistries.ITEM.get(id);

			for (final Acquisition.Data.Loot rule : entry.getValue()) {
				if (!rule.table.equals(table))
					continue;

				final LootItemCondition.Builder biome = biomeCondition(rule);

				// An unresolvable biome must not silently drop the item everywhere.
				if (rule.biome != null && biome == null)
					continue;

				LootItemCondition.Builder conditions =
						LootItemRandomChanceCondition.randomChance(
								(float)rule.chance);

				if (biome != null)
					conditions = conditions.and(biome);

				final LootPool pool = LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(item).when(conditions))
						.build();

				event.getTable().addPool(pool);
			}
		}
	}

	private static LootItemCondition.Builder biomeCondition(
			final Acquisition.Data.Loot rule) {
		if (rule.biome == null)
			return null;

		final ResourceLocation id = ResourceLocation.tryParse(rule.biome);

		if (id == null)
			return null;

		return LocationCheck.checkLocation(LocationPredicate.Builder.location()
				.setBiome(ResourceKey.create(Registries.BIOME, id)));
	}
}
