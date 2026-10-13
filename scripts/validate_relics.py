#!/usr/bin/env python3
"""
Validate Vital Relics translations, recipes, and loot-table acquisitions.

Run from the repository root:

    python3 scripts/validate_relic_acquisitions.py

Optionally provide another repository root:

    python3 scripts/validate_relic_acquisitions.py /path/to/vital-relics
"""

from __future__ import annotations

import argparse
import json
import re
import sys

from pathlib import Path
from typing import Any


LOCALES = ("en_us", "ja_jp", "zh_cn", "zh_tw")
VALID_RARITIES = {"common", "uncommon", "rare", "epic"}
VALID_CURIO_SLOTS = {
    "ring",
    "bracelet",
    "charm",
    "pendant",
    "necklace",
    "head",
    "back",
    "feet",
}

ITEM_ID_PATTERN = re.compile(r"^[a-z0-9_.-]+:[a-z0-9/._-]+$")
LOOT_TABLE_PATTERN = re.compile(
    r"^(?P<namespace>[a-z0-9_.-]+):(?P<path>[a-z0-9/._-]+)$"
)


class ValidationError(Exception):
    pass


class DuplicateKeyError(ValidationError):
    pass


def fail(message: str) -> None:
    raise ValidationError(message)


def duplicate_check(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}

    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(f"Duplicate JSON key: {key}")

        result[key] = value

    return result


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"Missing file: {path}")

    try:
        return json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=duplicate_check,
        )
    except DuplicateKeyError as exc:
        fail(f"{path}: {exc}")
    except json.JSONDecodeError as exc:
        fail(f"{path}:{exc.lineno}:{exc.colno}: invalid JSON: {exc.msg}")

    raise AssertionError("unreachable")


def require_dict(value: Any, description: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{description} must be an object")

    return value


def require_list(value: Any, description: str) -> list[Any]:
    if not isinstance(value, list):
        fail(f"{description} must be an array")

    return value


def require_nonempty_string(value: Any, description: str) -> str:
    if not isinstance(value, str) or not value.strip():
        fail(f"{description} must be a non-empty string")

    return value


def check_item_id(value: Any, description: str) -> None:
    item_id = require_nonempty_string(value, description)

    if not ITEM_ID_PATTERN.fullmatch(item_id):
        fail(
            f"{description} has invalid item ID {item_id!r}; "
            "expected namespace:path"
        )


def check_translation(
    translations: dict[str, Any],
    key: str,
    locale: str,
    path: Path,
) -> None:
    if key not in translations:
        fail(f"{path}: missing translation key {key!r}")

    value = translations[key]

    if not isinstance(value, str) or not value.strip():
        fail(f"{path}: translation {key!r} must be a non-empty string")


def validate_relics(
    resources: Path,
) -> tuple[dict[str, dict[str, Any]], set[str]]:
    path = resources / "relics.json"
    root = require_dict(load_json(path), str(path))

    relic_list = require_list(root.get("relics"), f"{path}: relics")

    relics: dict[str, dict[str, Any]] = {}

    for index, raw_relic in enumerate(relic_list):
        description = f"{path}: relics[{index}]"
        relic = require_dict(raw_relic, description)

        relic_id = require_nonempty_string(
            relic.get("id"),
            f"{description}.id",
        )

        if relic_id in relics:
            fail(f"{path}: duplicate relic ID {relic_id!r}")

        rarity = relic.get("rarity", "common")
        if rarity not in VALID_RARITIES:
            fail(
                f"{description}.rarity has invalid value {rarity!r}; "
                f"expected one of {sorted(VALID_RARITIES)}"
            )

        curio_slot = relic.get("curio_slot")
        if curio_slot is not None and curio_slot not in VALID_CURIO_SLOTS:
            fail(
                f"{description}.curio_slot has invalid value "
                f"{curio_slot!r}"
            )

        texture = require_nonempty_string(
            relic.get("texture"),
            f"{description}.texture",
        )

        texture_path = resources / "textures" / texture
        if not texture_path.is_file():
            fail(
                f"{description}.texture refers to missing file: "
                f"{texture_path}"
            )

        for field in (
            "passive_skills",
            "properties",
            "callbacks",
            "available_spells",
        ):
            if field in relic:
                require_dict(relic[field], f"{description}.{field}")

        relics[relic_id] = relic

    materials_root = require_dict(
        load_json(resources / "materials.json"),
        str(resources / "materials.json"),
    )

    materials_list = require_list(
        materials_root.get("materials"),
        "materials.json: materials",
    )

    material_ids: set[str] = set()

    for index, raw_material in enumerate(materials_list):
        material = require_dict(
            raw_material,
            f"materials.json: materials[{index}]",
        )

        material_id = require_nonempty_string(
            material.get("id"),
            f"materials.json: materials[{index}].id",
        )

        if material_id in material_ids:
            fail(f"materials.json: duplicate material ID {material_id!r}")

        if material_id in relics:
            fail(
                f"ID {material_id!r} is used by both a relic and a material"
            )

        material_ids.add(material_id)

    return relics, material_ids


def validate_translations(
    resources: Path,
    relics: dict[str, dict[str, Any]],
) -> None:
    translations: dict[str, dict[str, Any]] = {}

    for locale in LOCALES:
        path = resources / "lang" / f"{locale}.json"
        translations[locale] = require_dict(load_json(path), str(path))

    for locale in LOCALES:
        path = resources / "lang" / f"{locale}.json"
        lang = translations[locale]

        for relic_id, relic in relics.items():
            check_translation(
                lang,
                f"item.vitalrelics.{relic_id}",
                locale,
                path,
            )
            check_translation(
                lang,
                f"tooltip.vitalrelics.{relic_id}",
                locale,
                path,
            )

            for skill_id in relic.get("passive_skills", {}):
                check_translation(
                    lang,
                    f"relic.vitalrelics.passive_skill.{skill_id}",
                    locale,
                    path,
                )

            for property_id in relic.get("properties", {}):
                check_translation(
                    lang,
                    f"relic.vitalrelics.property.{property_id}",
                    locale,
                    path,
                )

            for callback_id in relic.get("callbacks", {}):
                check_translation(
                    lang,
                    f"relic.vitalrelics.callback.{callback_id}",
                    locale,
                    path,
                )

            for spell_id in relic.get("available_spells", {}):
                check_translation(
                    lang,
                    f"relic.vitalrelics.spell.{spell_id}",
                    locale,
                    path,
                )


def validate_recipe_key(
    recipe_id: str,
    recipe: dict[str, Any],
    valid_targets: set[str],
    relics: dict[str, dict[str, Any]],
) -> None:
    if recipe_id not in valid_targets:
        fail(
            f"recipes.json: recipe {recipe_id!r} does not refer to "
            "a relic or material"
        )

    recipe_type = recipe.get("type")

    if recipe_type not in {"shaped", "shapeless"}:
        fail(
            f"recipes.json: {recipe_id!r} has unsupported type "
            f"{recipe_type!r}"
        )

    count = recipe.get("count")
    if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
        fail(
            f"recipes.json: {recipe_id!r}.count must be a positive integer"
        )

    ingredients: list[str] = []

    if recipe_type == "shaped":
        pattern = require_list(
            recipe.get("pattern"),
            f"recipes.json: {recipe_id}.pattern",
        )

        if not pattern or len(pattern) > 3:
            fail(
                f"recipes.json: {recipe_id}.pattern must contain "
                "between 1 and 3 rows"
            )

        if any(not isinstance(row, str) for row in pattern):
            fail(
                f"recipes.json: {recipe_id}.pattern rows must be strings"
            )

        widths = {len(row) for row in pattern}
        if len(widths) != 1 or next(iter(widths)) == 0:
            fail(
                f"recipes.json: {recipe_id}.pattern rows must have "
                "the same non-zero width"
            )

        width = next(iter(widths))
        if width > 3:
            fail(
                f"recipes.json: {recipe_id}.pattern rows may not exceed "
                "3 characters"
            )

        keys = require_dict(
            recipe.get("key"),
            f"recipes.json: {recipe_id}.key",
        )

        used_symbols = set("".join(pattern)) - {" "}

        if set(keys) != used_symbols:
            missing = sorted(used_symbols - set(keys))
            unused = sorted(set(keys) - used_symbols)

            fail(
                f"recipes.json: {recipe_id}.key mismatch; "
                f"missing={missing}, unused={unused}"
            )

        for symbol, item_id in keys.items():
            if not isinstance(symbol, str) or len(symbol) != 1:
                fail(
                    f"recipes.json: {recipe_id}.key symbols must "
                    "be one-character strings"
                )

            check_item_id(
                item_id,
                f"recipes.json: {recipe_id}.key[{symbol!r}]",
            )
            ingredients.append(item_id)

    else:
        raw_ingredients = require_list(
            recipe.get("ingredients"),
            f"recipes.json: {recipe_id}.ingredients",
        )

        if not raw_ingredients:
            fail(
                f"recipes.json: {recipe_id}.ingredients cannot be empty"
            )

        if len(raw_ingredients) > 9:
            fail(
                f"recipes.json: {recipe_id}.ingredients cannot contain "
                "more than 9 entries"
            )

        for index, item_id in enumerate(raw_ingredients):
            check_item_id(
                item_id,
                f"recipes.json: {recipe_id}.ingredients[{index}]",
            )
            ingredients.append(item_id)

    valid_internal_ids = {
        f"vitalrelics:{target}" for target in valid_targets
    }

    for item_id in ingredients:
        if item_id.startswith("vitalrelics:") and item_id not in valid_internal_ids:
            fail(
                f"recipes.json: {recipe_id!r} references unknown "
                f"Vital Relics item {item_id!r}"
            )

    if recipe_id in relics:
        expected_core = (
            f"vitalrelics:{relics[recipe_id].get('rarity', 'common')}"
            "_relic_core"
        )

        if expected_core not in ingredients:
            fail(
                f"recipes.json: relic {recipe_id!r} must use "
                f"{expected_core!r}"
            )


def validate_loot(
    loot_root: Any,
    valid_targets: set[str],
    path: Path,
) -> set[str]:
    loot = require_dict(loot_root, f"{path}: loot")
    loot_targets: set[str] = set()

    for relic_id, raw_rules in loot.items():
        if relic_id not in valid_targets:
            fail(
                f"{path}: loot entry {relic_id!r} does not refer to "
                "a relic or material"
            )

        rules = require_list(
            raw_rules,
            f"{path}: loot.{relic_id}",
        )

        if not rules:
            fail(f"{path}: loot.{relic_id} cannot be empty")

        for index, raw_rule in enumerate(rules):
            rule = require_dict(
                raw_rule,
                f"{path}: loot.{relic_id}[{index}]",
            )

            table = require_nonempty_string(
                rule.get("table"),
                f"{path}: loot.{relic_id}[{index}].table",
            )

            match = LOOT_TABLE_PATTERN.fullmatch(table)
            if not match:
                fail(
                    f"{path}: loot.{relic_id}[{index}].table has invalid "
                    f"resource ID {table!r}"
                )

            chance = rule.get("chance")
            if (
                not isinstance(chance, (int, float))
                or isinstance(chance, bool)
                or not 0.0 < float(chance) <= 1.0
            ):
                fail(
                    f"{path}: loot.{relic_id}[{index}].chance must be "
                    "greater than 0 and at most 1"
                )

        if relic_id in valid_targets:
            loot_targets.add(relic_id)

    return loot_targets



RELICS__INTENTIONALLY_WITHOUT_ACQUISITIONS = [
    "reality_rend",
    "undead_badge",
    "skeletal_badge",
    "arthropod_badge",
    "ooze_badge",
    "raider_badge",
    "evoker_badge",
    "witch_badge",
    "piglin_badge",
    "nether_beast_badge",
    "withered_badge",
    "blaze_badge",
    "ghast_badge",
    "end_badge",
    "guardian_badge",
    "creeper_badge",
    "phantom_badge",
    "breeze_badge",
    "creaking_badge",
    "ravager_badge",
    "elder_guardian_badge",
    "warden_badge",
    "wither_badge",
    "dragon_badge",
]

def validate_acquisitions(
    resources: Path,
    relics: dict[str, dict[str, Any]],
    materials: set[str],
) -> tuple[int, int]:
    path = resources / "recipes.json"
    root = require_dict(load_json(path), str(path))

    recipes = require_dict(
        root.get("recipes"),
        f"{path}: recipes",
    )

    loot = root.get("loot", {})
    undefined_raw = root.get("undefined", [])
    undefined = set(
        require_list(undefined_raw, f"{path}: undefined")
    )

    valid_targets = set(relics) | materials

    for target in undefined:
        if not isinstance(target, str) or target not in relics:
            fail(
                f"{path}: undefined entry {target!r} is not an existing relic"
            )

    for recipe_id, raw_recipe in recipes.items():
        recipe = require_dict(
            raw_recipe,
            f"{path}: recipes.{recipe_id}",
        )
        validate_recipe_key(recipe_id, recipe, valid_targets, relics)

    loot_targets = validate_loot(loot, valid_targets, path)

    for relic_id in relics:
        if relic_id in RELICS__INTENTIONALLY_WITHOUT_ACQUISITIONS:
            continue

        has_recipe = relic_id in recipes
        has_loot = relic_id in loot_targets
        is_undefined = relic_id in undefined

        if not (has_recipe or has_loot or is_undefined):
            fail(
                f"Relic {relic_id!r} has no acquisition route: "
                "add a recipe, loot entry, or recipes.json undefined entry"
            )

    return len(recipes), len(loot_targets)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "root",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Vital Relics repository root",
    )
    args = parser.parse_args()

    root = args.root.resolve()
    resources = root / "common" / "src" / "main" / "resources" / "vitalrelics"

    try:
        relics, materials = validate_relics(resources)
        validate_translations(resources, relics)
        recipe_count, loot_count = validate_acquisitions(
            resources,
            relics,
            materials,
        )
    except ValidationError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1

    print(
        "PASS: "
        f"{len(relics)} relics, "
        f"{len(materials)} materials, "
        f"{recipe_count} recipes, "
        f"{loot_count} loot entries, "
        f"{len(LOCALES)} translations"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())