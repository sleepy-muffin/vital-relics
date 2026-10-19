package com.example.vitalrelics.gametest;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.Loader;
import com.example.vitalrelics.common.relics.Relic;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Since NeoForge 1.21.8 the {@code @GameTest} annotations are gone: tests are registered on the
 * mod event bus instead. The tests only inspect registry/data state, so they do not depend on
 * world content. They reference the tiny {@code vitalrelics:empty} structure shipped by the common
 * module.</p>
 */
public final class VitalGameTests {
	private VitalGameTests() {}

	public static void register(final RegisterGameTestsEvent event) {
		final Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
				ResourceLocation.fromNamespaceAndPath(Manifest.MODID, "default"),
				new TestEnvironmentDefinition.AllOf()
		);

		event.registerTest(
				ResourceLocation.fromNamespaceAndPath(Manifest.MODID, "smoke"),
				new SmokeTestInstance(new TestData<>(
						environment,
						ResourceLocation.fromNamespaceAndPath(Manifest.MODID, "empty"),
						200, 1, true
				))
		);
	}

	private static final class SmokeTestInstance extends GameTestInstance {
		private SmokeTestInstance(final TestData<Holder<TestEnvironmentDefinition>> info) {
			super(info);
		}

		@Override
		public void run(final GameTestHelper helper) {
			int checked = 0;

			for (final Relic relic : Loader.get().relics_) {
				final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Manifest.MODID, relic.id);

				helper.assertTrue(
						BuiltInRegistries.ITEM.containsKey(id),
						Component.literal("missing relic item: " + relic.id)
				);

				checked++;
			}

			helper.assertTrue(checked > 0, Component.literal("no relic items were registered"));

			final var rules = Acquisition.get().data.loot.get("cherry_cross");
			helper.assertTrue(rules != null && !rules.isEmpty(), Component.literal("cherry_cross loot entry is missing"));

			helper.succeed();
		}

		@Override
		public MapCodec<? extends GameTestInstance> codec() {
			return MapCodec.unit(this);
		}

		@Override
		protected MutableComponent typeDescription() {
			return Component.literal("Vital Relics smoke test");
		}
	}
}
