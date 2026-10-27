package com.example.vitalrelics.common.relics;

import java.util.List;

/**
 * Version-tolerant aliases for vanilla items that were renamed between the
 * Minecraft versions this project targets.
 * <p>
 * Recipes refer to a single item ID, but that ID is not valid everywhere:
 * for example {@code minecraft:chain} became {@code minecraft:iron_chain}.
 * Grouping the IDs keeps one shared recipe definition usable on every loader.
 */
public final class ItemIdAliases {
	private ItemIdAliases() {}

	/*
	 * Every group lists the ID used by the shared recipe definitions first,
	 * followed by the IDs that replaced it in newer versions.
	 */
	private static final List<List<String>> GROUPS = List.of(
			List.of("minecraft:chain", "minecraft:iron_chain")
	);

	/**
	 * The preferred ID for {@code id}, followed by its equivalents.
	 */
	public static List<String> candidates(final String id) {
		if (id == null)
			return List.of();

		for (final List<String> group : GROUPS)
			if (group.contains(id))
				return group;

		return List.of(id);
	}

	/**
	 * The version-independent form of {@code id}, used when comparing IDs.
	 */
	public static String canonical(final String id) {
		final List<String> candidates = candidates(id);
		return candidates.isEmpty() ? id : candidates.get(0);
	}

	public static boolean matches(final String expected, final String actual) {
		if (expected == null || actual == null)
			return false;

		return canonical(expected).equals(canonical(actual));
	}
}
