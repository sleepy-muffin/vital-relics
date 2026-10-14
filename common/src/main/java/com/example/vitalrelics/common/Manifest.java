package com.example.vitalrelics.common;

import java.util.List;

/**
 * Central registry of mod-wide constants: the mod id, version strings,
 * internal resource paths, and commonly used Minecraft IDs.
 * <p>
 * Kept intentionally dependency-free so it can be referenced from every loader module.
 */
public class Manifest {
	public static final String MODID = "vitalrelics";

	// Source code no-longer tracks it. As it does not help.
	// public static final String VERSION = "0.7.14";

	// Increase these versions only when the corresponding configuration format changes.
	// Ordinary additions or edits to built-in definitions do not require a version bump.
	// Configurations with "customized": false are updated automatically; configurations
	// with "customized": true must never be overwritten automatically.
	public static final String OPT_RELICS_VER = "0.6.7";
	public static final String OPT_RECIPES_VER = "0.6.3";
	public static final String OPT_LANG_VER = "0.6.2";
	public static final String OPT_MATERIAL_VER = "0.1.0";

	public static final String INTERNAL_PATH_TO_RELICS = "vitalrelics/relics.json";
	public static final String INTERNAL_PATH_TO_RECIPES = "vitalrelics/recipes.json";
	public static final String INTERNAL_PATH_TO_MATERIAL = "vitalrelics/materials.json";

	public static final List<String> DEFAULT_LOCALES = List.of(
			"en_us", "zh_cn", "zh_tw", "ja_jp"
	);

	public static String INTERNAL_PATH_TO_LANG(final String locale) {
		return "vitalrelics/lang/" + locale + ".json";
	}


	public static final String ENEMY_RELICS_TAG = "VitalRelicsEnemyRelics";
	public static final String ENEMY_RELICS_ROLLED_TAG = "VitalRelicsEnemyRelicsRolled";

	/*
	Minecraft IDs
	 */
	public static final String EFFECT_ABSORPTION = "absorption";
	public static final String EFFECT_SLOWNESS = "slowness";
	public static final String EFFECT_WITHER = "wither";
	public static final String EFFECT_WEAKNESS = "weakness";
	public static final String EFFECT_DARKNESS = "darkness";
}
