package com.example.vitalrelics.common.relics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.vitalrelics.common.RelicText.displayName;

public class Relic {
	/*
	 * Do not remove my comments.
	 *
	 * Design Strength Table
	 *                  Common   Uncommon   Rare   Epic
	 * Ring              1.0       1.5      2.2     xxx
	 * Bracelet          1.2       1.8      2.6     xxx
	 * Feet/Anklet       xxx       2.0      3.0     8.5
	 * Charm/Pendant     1.3       2.0      3.0     8.5
	 * Necklace          xxx       2.3      3.4     10.0
	 * Head              xxx       2.3      3.4     10.0
	 *
	 * So, we'll add new relics for necklace and head later right?
	 *   it shouldn't be halos for head relics if they are not epic.
	 *
	 */

	public String id;
	public String display_name = null;
	public final List<String> effective_slots = new ArrayList<>();
	public String curio_slot = "charm";
	public String tooltip = "Placeholder tooltip";
	public String rarity = "common";
	public String texture = "brown_ring.png";

	public final List<String> immune_to_effects = new ArrayList<>();
	public final Map<String, Integer> granted_effects = new LinkedHashMap<>();

	/*
	Passive Skills
	 */

	/**
	 * Reflected arrow minimum damage = ATTACK_DAMAGE × level
	 */
	public static final String PASSIVE_SKILL_RETARGET_ARROW = "retarget_arrow";
    /**
     * level% attack-damage contribution, level-block radius,
	 *      roughly level/4 debuff strength
     */
	public static final String PASSIVE_SKILL_REALITY_SEVERANCE = "reality_severance";
    /**
     * Repairs up to level durability every 4 seconds.
     */
	public static final String PASSIVE_SKILL_METAL_MENDING = "metal_mending";
    /**
     * Any level > 0 grants flight; flight speed = vanilla flight speed × level,
	 *      does not change the speed if the level is 1.0
     */
	public static final String PASSIVE_SKILL_FLIGHT = "flight";
    /**
     * Multiplies base damage by level
     */
	public static final String PASSIVE_SKILL_EMPOWERED_ARROW = "empowered_arrows";
	/**
	 * Multiplies speed by level
	 */
	public static final String PASSIVE_SKILL_THRUSTING_ARROW = "thrusting_arrows";
    /**
     * Makes every arrow fired by the bearer ignore gravity and
	 *      disappear after `level` seconds, including arrows fired by relic spells
     */
	public static final String PASSIVE_SKILL_GRAVITYLESS_ARROWS = "gravityless_arrows";
    /**
     * Heals the bearer for damage dealt × level
     */
	public static final String PASSIVE_SKILL_LIFESTEAL = "lifesteal";

    /**
     * Reflects received damage × level; reflection is limited by a cooldown.
     */
	public static final String PASSIVE_SKILL_THORNS = "thorns";

    /**
     * Reflects one incoming arrow; reflected damage and speed are
	 *      multiplied by level, and cooldown is 5 / level seconds
     */
	public static final String PASSIVE_SKILL_ARROW_DEFLECTION = "arrow_deflection";
    /**
     * Extinguish fire.
     */
	public static final String PASSIVE_SKILL_FIRE_RESISTANCE = "fire_resistance";

    /**
     * Not Implemented
     */
	public static final String PASSIVE_SKILL_LAVA_SWIMMER = "lava_swimmer";

    /**
     * ticks = Math.round(level)
	 * `iron_curtain` is no longer a boolean value, it does not demand invincibleTimeTaken anymore.
	 * On receiving damage, the bearer ignores the all following damages within `ticks` by setting
	 *      the damage to `0`.
     */
	public static final String PASSIVE_SKILL_IRON_CURTAIN = "iron_curtain";

    /**
     * Accumulates a portion of damage dealt as a temporary wound that
	 *      reduces the target's effective maximum health,
	 *      preventing healing above the remaining health limit.
	 *      Extra damage can accumulate the wound twice,
	 *      allowing it to receive both the original attack's accumulation
	 *      and an additional accumulation from the extra damage.
     */
	public static final String PASSIVE_SKILL_LINGERING_WOUND = "lingering_wound";
    /**
	 * victim.igniteForSeconds(Math.max(1, (int) Math.round(cinderBrandLevel)));
     * Enemy attacked by you will be set fire on hit.
     */
	public static final String PASSIVE_SKILL_CINDER_BRAND = "cinder_brand";
    /**
     * Every half second, moves nearby entities downward into the ground.
     */
	public static final String PASSIVE_SKILL_GRAVE_DOMINION = "grave_dominion";
    /**
     * Multiplies positive experience gains by
	 *      `1 + level × (experience needed for the next level / 7 - 1)`,
	 *      making experience-level progression approach a linear curve.
     */
	public static final String PASSIVE_SKILL_EXPERIENCE_CONVERGENCE = "experience_convergence";

    /**
     * Shares each configured `heal` periodic action with allied living
	 *      entities within level blocks. This skill does not produce healing by itself.
     */
	public static final String PASSIVE_SKILL_HEALING_AURA = "healing_aura";

	/**
	 * Every half second, moves hostile living entities within level blocks
	 *      onto the first collidable surface beneath them.
	 *      The radius in blocks equals the skill level.
	 */
	public static final String PASSIVE_SKILL_NO_FLY_ZONE = "no_fly_zone";

    /**
     * Every 20 ticks, applies Slowness III for 60 ticks to hostile
	 *      living entities within `level` blocks.
     */
	public static final String PASSIVE_SKILL_SLOWING_AURA = "slowing_aura";
    /**
     * Any positive level enables bounded, connected-log felling with
	 *      the player's held tool.
     */
	public static final String PASSIVE_SKILL_TREE_FELLER = "tree_feller";
    /**
     * Mines nearby blocks within `level` blocks while preserving
	 *      the player's held tool and normal mining context.
     */
	public static final String PASSIVE_SKILL_AREA_MINING = "area_mining";
	/**
	 * Enemy cannot aim at you within level range.
	 */
	public static final String PASSIVE_SKILL_SUFFOCATION_ZONE = "suffocation_zone";
	/**
	 * Not Implemented
	 */
	public static final String PASSIVE_SKILL_ARMOR_AURA = "armor_aura";

    /**
     *  Shares the wearer's damage reduction with nearby allies.
     */
	public static final String PASSIVE_SKILL_DAMAGE_REDUCTION_AURA = "damage_reduction_aura";



	public final Map<String, Double> passive_skills = new LinkedHashMap<>();

	/*
	Spells
	 */
	public static final String SPELL_TELEPORT = "teleport";
	public static final String SPELL_CURSE = "curse";
	public static final String SPELL_HEAL = "heal";
	public static final String SPELL_HEALING_RAY = "healing_ray";
	public static final String SPELL_CLEANSE = "cleanse";
	public static final String SPELL_DASH = "dash";
	public static final String SPELL_ARC_BURST = "arc_burst";
	public static final String SPELL_REPULSE = "repulse";
	public static final String SPELL_ABSORPTION = "absorption";
	public static final String SPELL_SKY_LAUNCH = "sky_launch";
	public static final String SPELL_SHADOW_EXCHANGE = "shadow_exchange";
	public static final String SPELL_GRAVE_SHIFT = "grave_shift";
	public static final String SPELL_PHANTOM_STEP = "phantom_step";
	public static final String SPELL_UPGRADE_ENCHANTED_BOOK = "upgrade_enchanted_book";
	public static final String SPELL_ENCHANTMENT_ASCENSION = "enchantment_ascension";
	public static final String SPELL_PURIFY_CURSE = "purify_curse";
	public static final String SPELL_PURIFY_PENALTY = "purify_penalty";
	public static final String SPELL_DISENCHANTMENT = "disenchantment";
	public static final String SPELL_OPEN_ENDER_CHEST = "open_ender_chest";
	public static final String SPELL_RETURN_TO_BED = "return_to_bed";
	public static final String SPELL_BOREBOLT = "borebolt";
	public static final String SPELL_COMPEL_ATTACK = "compel_attack";

	public final Map<String, Spells.Info> available_spells = new LinkedHashMap<>();


	public final Map<String, Properties.Info> properties = new LinkedHashMap<>();
	public final Map<String, Ticks.Info> ticks = new LinkedHashMap<>();
	public final Map<String, Callbacks.Info> callbacks = new LinkedHashMap<>();
	public final Map<String, Double> enemy_spawn = new LinkedHashMap<>();

	public static class Properties {
		public static class Info {
			public Double add = null;
			public Double mul_base = null;
			public Double mul_total = null;

			public static Info basic() {
				final Info result = new Info();
				result.add = 0.0;
				result.mul_base = 0.0;
				result.mul_total = 1.0;
				return result;
			}
		}
	}

	public static class Ticks {
		public static class Info {
			public int interval_ticks = 1;
			public Double add = null;
			public Double ratio_add = null;

			public static Info basic() {
				final Info result = new Info();
				result.interval_ticks = 1;
				result.add = 0.0;
				result.ratio_add = 0.0;
				return result;
			}
		}
	}

	public static class Callbacks {
		public static class Info {
			public Double modifier = null;
			public Double flat = null;
			public Double minimum = null;
			public Double ratio_minimum = null;
			public Double maximum = null;
			public Double ratio_maximum = null;

			@Deprecated(since = "Use `applyFlat`, `applyModifier`, `clamp` manually instead. ")
			public double process(final double amount, final double reference) {
				double result = amount;

				if (modifier != null)
					result *= modifier;
				if (flat != null)
					result += flat;

				return sigmoidClamp(result, lowerBound(reference), upperBound(reference));
			}

			public double applyFlat(final double amount) {
				return flat != null ? amount + flat : amount;
			}

			public double applyModifier(final double amount) {
				return modifier != null ? amount * modifier : amount;
			}

			public double clamp(final double value, final double reference) {
				return sigmoidClamp(value, lowerBound(reference), upperBound(reference));
			}

			private double lowerBound(final double reference) {
				double lower = Double.NEGATIVE_INFINITY;

				if (minimum != null)
					lower = Math.max(lower, minimum);
				if (ratio_minimum != null)
					lower = Math.max(lower, reference * ratio_minimum);

				return lower;
			}

			private double upperBound(final double reference) {
				double upper = Double.POSITIVE_INFINITY;

				if (maximum != null)
					upper = Math.min(upper, maximum);
				if (ratio_maximum != null)
					upper = Math.min(upper, reference * ratio_maximum);

				return upper;
			}

			private static double sigmoidClamp(
					final double value,
					final double minimum,
					final double maximum) {

				if (Double.isInfinite(minimum) && Double.isInfinite(maximum))
					return value;

				if (Double.isInfinite(minimum))
					return maximum - Math.log1p(Math.exp(maximum - value));

				if (Double.isInfinite(maximum))
					return minimum + Math.log1p(Math.exp(value - minimum));

				if (minimum >= maximum)
					return minimum;

				final double center = (minimum + maximum) * 0.5;
				final double range = maximum - minimum;

				return minimum + range /
						(1.0 + Math.exp(-4.0 * (value - center) / range));
			}
		}
	}

	public static class Spells {
		public static class Info {
			public final Map<String, Object> parameters = new LinkedHashMap<>();
		}
	}

	public boolean isImmuneToEffect(
			final String effectId,
			final boolean negative) {

		return immune_to_effects.contains(effectId) ||
				(negative && immune_to_effects.contains("all_negative"));
	}

	public static String itemDisplayName(final String id) {
		return displayName(id);
	}
}
