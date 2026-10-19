package com.example.vitalrelics.gametest;

import com.example.vitalrelics.common.Manifest;
import com.example.vitalrelics.common.relics.Acquisition;
import com.example.vitalrelics.common.relics.Loader;
import com.example.vitalrelics.common.relics.Relic;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
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
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Headless smoke tests, run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Since NeoForge 1.21.8 the {@code @GameTest} annotations are gone: tests are registered on the
 * mod event bus instead. The tests only inspect registry/data state, so they do not depend on
 * world content. They reference the tiny {@code vitalrelics:empty} structure shipped by the common
 * module.</p>
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

	public static void registerFunctions(final RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, SMOKE, () -> VitalGameTests::smoke);
		event.register(Registries.TEST_FUNCTION, LOOT, () -> VitalGameTests::lootIsWellFormed);
		event.register(Registries.TEST_FUNCTION, SYNCABLE, () -> VitalGameTests::testInstancesAreSyncable);
	}

	public static void registerTests(final RegisterGameTestsEvent event) {
		final Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
				id("default"),
				new TestEnvironmentDefinition.AllOf()
		);

		event.registerTest(SMOKE, test(SMOKE, environment));
		event.registerTest(LOOT, test(LOOT, environment));
		event.registerTest(SYNCABLE, test(SYNCABLE, environment));
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
