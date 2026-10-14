package com.example.vitalrelics.acquisition;

import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.Manifest;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;

public final class AcquisitionEvents {
	private AcquisitionEvents() {}

	@SubscribeEvent
	public static void onLootTableLoad(final LootTableLoadEvent event) {
		final String table = event.getName().toString();

		for (final var entry : Acquisition.get().data.loot.entrySet()) {
			final ResourceLocation id =
					ResourceLocation.fromNamespaceAndPath(Manifest.MODID, entry.getKey());

			if (!BuiltInRegistries.ITEM.containsKey(id))
				continue;

			final var item = BuiltInRegistries.ITEM.get(id);

			if (item.isEmpty())
				continue;

			final Item nativeItem = item.get().value();

			for (final Acquisition.Data.Loot rule : entry.getValue()) {
				if (!rule.table.equals(table))
					continue;

				final LootItemCondition.Builder biome =
						biomeCondition(event.getRegistries(), rule);

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
						.add(LootItem.lootTableItem(nativeItem).when(conditions))
						.build();

				event.getTable().addPool(pool);
			}
		}
	}

	private static LootItemCondition.Builder biomeCondition(
			final HolderLookup.Provider registries,
			final Acquisition.Data.Loot rule) {
		if (rule.biome == null)
			return null;

		final ResourceLocation id = ResourceLocation.tryParse(rule.biome);

		if (id == null)
			return null;

		return registries.lookupOrThrow(Registries.BIOME)
				.get(ResourceKey.create(Registries.BIOME, id))
				.map(holder -> LocationCheck.checkLocation(
						LocationPredicate.Builder.inBiome(holder)))
				.orElse(null);
	}

//	@SubscribeEvent
//	public static void onRecipesUpdated(final RecipesUpdatedEvent event) {
//		System.out.println("Recipes updated");
//
//		for (final var recipe : event.getRecipeManager().getRecipes()) {
//			if (recipe.id().getNamespace().equals(VitalRelics.MODID)) {
//				System.out.println(
//						recipe.id() + " : " +
//								recipe.value().getClass().getName()
//				);
//			}
//		}
//	}
}
