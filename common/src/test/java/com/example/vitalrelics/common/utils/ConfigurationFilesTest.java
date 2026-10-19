package com.example.vitalrelics.common.utils;

import com.example.vitalrelics.common.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the configuration bootstrap logic.
 *
 * <p>These tests exercise {@link ConfigurationFiles#load_external_file} directly, so they do not
 * depend on any loader platform or on a running game.</p>
 */
class ConfigurationFilesTest {

	private static Path write(final Path dir, final String name, final String content) throws IOException {
		final Path file = dir.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	@Test
	void bundledFileIsCopiedAndMetadataAdded(@TempDir final Path dir) {
		final Path target = dir.resolve("recipes.json");

		final Map<String, Object> root = ConfigurationFiles.load_external_file(
				Manifest.INTERNAL_PATH_TO_RECIPES, target, Manifest.OPT_RECIPES_VER);

		assertTrue(Files.exists(target), "the external file must be created");
		assertTrue(root.containsKey("_meta"), "metadata must be added");

		final Map<?, ?> meta = (Map<?, ?>) root.get("_meta");
		assertEquals(Manifest.OPT_RECIPES_VER, meta.get("version"));
		assertEquals(Boolean.FALSE, meta.get("customized"));
		assertTrue(root.containsKey("loot"), "bundled loot must be present");
	}

	@Test
	void nonCustomizedFileIsRefreshedFromBundled(@TempDir final Path dir) throws IOException {
		final Path target = write(dir, "recipes.json",
				"{\n  \"_meta\": { \"version\": \"" + Manifest.OPT_RECIPES_VER + "\", \"customized\": false },\n  \"junk\": true\n}\n");

		final Map<String, Object> root = ConfigurationFiles.load_external_file(
				Manifest.INTERNAL_PATH_TO_RECIPES, target, Manifest.OPT_RECIPES_VER);

		assertFalse(root.containsKey("junk"), "non-customized files must be overwritten");
		assertTrue(root.containsKey("loot"), "the bundled contents must be restored");
	}

	@Test
	void legacyCustomizedFileIsMigratedAndPreserved(@TempDir final Path dir) throws IOException {
		final Path target = write(dir, "recipes.json",
				"{\n  \"customized\": true,\n  \"loot\": {}\n}\n");

		final Map<String, Object> root = ConfigurationFiles.load_external_file(
				Manifest.INTERNAL_PATH_TO_RECIPES, target, Manifest.OPT_RECIPES_VER);

		assertFalse(root.containsKey("customized"), "the legacy flag must be migrated into _meta");

		final Map<?, ?> meta = (Map<?, ?>) root.get("_meta");
		assertEquals(Manifest.OPT_RECIPES_VER, meta.get("version"));
		assertEquals(Boolean.TRUE, meta.get("customized"));
		assertTrue(root.containsKey("loot"), "customized contents must be preserved");
	}

	@Test
	void incompatibleCustomizedVersionThrows(@TempDir final Path dir) throws IOException {
		final Path target = write(dir, "recipes.json",
				"{\n  \"_meta\": { \"version\": \"0.0.1\", \"customized\": true },\n  \"loot\": {}\n}\n");

		assertThrows(ConfigurationException.class, () -> ConfigurationFiles.load_external_file(
				Manifest.INTERNAL_PATH_TO_RECIPES, target, Manifest.OPT_RECIPES_VER));
	}
}
