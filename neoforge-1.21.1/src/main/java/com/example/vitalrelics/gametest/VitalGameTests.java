package com.example.vitalrelics.gametest;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.Loader;
import com.example.vitalrelics.common.relics.Relic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}.
 *
 * <p>The tests only inspect registry/data state, so they do not depend on world content. They
 * reference the tiny {@code vitalrelics:empty} structure shipped by the common module, and avoid
 * the class-name prefix so that the structure id stays stable.</p>
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
}
