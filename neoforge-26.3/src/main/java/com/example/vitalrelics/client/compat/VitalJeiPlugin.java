package com.example.vitalrelics.client.compat;

import com.example.vitalrelics.VitalRelics;
import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.ItemIdAliases;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiShapedRecipeBuilder;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JeiPlugin
public final class VitalJeiPlugin implements IModPlugin {
	private static final Identifier UID =
			Identifier.fromNamespaceAndPath(Manifest.MODID, "jei_plugin");

	@Override
	public Identifier getPluginUid() {
		return UID;
	}

	@Override
	public void registerRecipes(final IRecipeRegistration registration) {
		final List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();

		for (final Map.Entry<String, Acquisition.Data.Crafting> entry :
				Acquisition.get().data.recipes.entrySet()) {

			final Identifier id =
					Identifier.fromNamespaceAndPath(Manifest.MODID, entry.getKey());

			final ItemStack output = itemStack(id, entry.getValue().count);

			if (output.isEmpty()) {
				VitalRelics.LOGGER.warn(
						"Skipping JEI recipe '{}': '{}' is not a registered item",
						entry.getKey(), id
				);

				continue;
			}

			/*
			 * A single malformed definition must not take down every recipe of
			 * the mod: JEI only reports the failure and drops the whole batch.
			 */
			try {
				final CraftingRecipe recipe = switch (entry.getValue().type) {
					case "shaped" -> shaped(registration, entry.getValue(), output);
					case "shapeless" -> shapeless(entry.getValue(), output);
					default -> null;
				};

				if (recipe == null)
					continue;

				final ResourceKey<Recipe<?>> recipeKey =
						ResourceKey.create(Registries.RECIPE, id);

				recipes.add(new RecipeHolder<>(recipeKey, recipe));
			} catch (final RuntimeException exception) {
				VitalRelics.LOGGER.error(
						"Skipping JEI recipe '{}': failed to build it",
						entry.getKey(), exception
				);
			}
		}

		VitalRelics.LOGGER.info(
				"Registering {} JEI crafting recipes from the recipe configuration",
				recipes.size()
		);

		registration.addRecipes(RecipeTypes.CRAFTING, recipes);
	}

	private static CraftingRecipe shaped(
			final IRecipeRegistration registration,
			final Acquisition.Data.Crafting definition,
			final ItemStack output) {

		final SlotDisplay outputDisplay =
				new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(output));

		final IJeiShapedRecipeBuilder builder = registration
				.getVanillaRecipeFactory()
				.createShapedRecipeBuilder(CraftingBookCategory.MISC, outputDisplay);

		for (final Map.Entry<String, String> entry : definition.key.entrySet()) {
			final Ingredient ingredient = ingredient(entry.getValue());

			if (ingredient == null)
				return null;

			builder.define(entry.getKey().charAt(0), ingredient);
		}

		for (final String row : definition.pattern)
			builder.pattern(row);

		return builder.build();
	}

	private static CraftingRecipe shapeless(
			final Acquisition.Data.Crafting definition,
			final ItemStack output) {

		final List<Ingredient> ingredients = new ArrayList<>();

		for (final String itemId : definition.ingredients) {
			final Ingredient ingredient = ingredient(itemId);

			if (ingredient == null)
				return null;

			ingredients.add(ingredient);
		}

		return new ShapelessRecipe(
				new Recipe.CommonInfo(false),
				new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
				ItemStackTemplate.fromNonEmptyStack(output),
				ingredients
		);
	}

	/**
	 * Resolves an ingredient through {@link ItemIdAliases}, so IDs that were
	 * renamed by Minecraft still display on the versions that renamed them.
	 *
	 * @return the ingredient, or {@code null} when no candidate ID is registered
	 */
	private static Ingredient ingredient(final String itemId) {
		for (final String candidate : ItemIdAliases.candidates(itemId)) {
			final Identifier id = Identifier.tryParse(candidate);

			if (id == null)
				continue;

			final Ingredient ingredient = BuiltInRegistries.ITEM
					.get(id)
					.map(holder -> Ingredient.of(holder.value()))
					.orElse(null);

			if (ingredient != null)
				return ingredient;
		}

		VitalRelics.LOGGER.warn(
				"Skipping JEI recipe: item ID '{}' is not registered", itemId
		);

		return null;
	}

	private static ItemStack itemStack(final Identifier id, final int count) {
		return BuiltInRegistries.ITEM
				.get(id)
				.map(holder -> new ItemStack(holder.value(), count))
				.orElse(ItemStack.EMPTY);
	}
}
