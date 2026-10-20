package com.example.vitalrelics.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the per-entity scheduler state.
 *
 * <p>{@link Scheduler#INSTANCE()} is process-wide, so every test uses a fresh
 * {@link UUID} to keep state isolated.</p>
 */
class SchedulerTest {

	private static final List<String> SPELLS = List.of("a", "b", "c");

	@Test
	void selectedSpellDefaultsToFirstEntry() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertEquals("a", scheduler.selectedSpell(uuid, SPELLS, 0));
		scheduler.clearEntity(uuid);
	}

	@Test
	void selectSpellFromNothingPicksSecondEntry() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		// indexOf(null) == -1, clamped to 0, then advanced by one.
		assertEquals("b", scheduler.selectSpell(uuid, SPELLS, 1, 0));
		scheduler.clearEntity(uuid);
	}

	@Test
	void selectSpellWrapsForwardAndBackward() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertEquals("a", scheduler.selectedSpell(uuid, SPELLS, 0));
		assertEquals("c", scheduler.selectSpell(uuid, SPELLS, -1, 1));
		assertEquals("a", scheduler.selectSpell(uuid, SPELLS, 1, 2));
		scheduler.clearEntity(uuid);
	}

	@Test
	void emptySpellListSelectsNothing() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertNull(scheduler.selectedSpell(uuid, List.of(), 0));
		assertNull(scheduler.selectSpell(uuid, List.of(), 1, 0));
		scheduler.clearEntity(uuid);
	}

	@Test
	void selectedSpellResetsWhenPreviousSelectionDisappears() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertEquals("b", scheduler.selectSpell(uuid, SPELLS, 1, 0));
		assertEquals("a", scheduler.selectedSpell(uuid, List.of("a", "c"), 1));
		scheduler.clearEntity(uuid);
	}

	@Test
	void spellCooldownCountsDownAndExpires() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		scheduler.setSpellCooldown(uuid, "fireball", 100, 40);

		assertEquals(40, scheduler.getSpellCooldownRemaining(uuid, "fireball", 100));
		assertEquals(20, scheduler.getSpellCooldownRemaining(uuid, "fireball", 120));
		assertEquals(0, scheduler.getSpellCooldownRemaining(uuid, "fireball", 140));
		assertEquals(0, scheduler.getSpellCooldownRemaining(uuid, "fireball", 500));
		assertEquals(0, scheduler.getSpellCooldownRemaining(uuid, "unknown", 100));
		assertEquals(0, scheduler.getSpellCooldownRemaining(UUID.randomUUID(), "fireball", 100));

		scheduler.clearEntity(uuid);
	}

	@Test
	void spellCooldownSetterOverwritesInsteadOfAccumulating() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		scheduler.setSpellCooldown(uuid, "fireball", 100, 40);
		scheduler.setSpellCooldown(uuid, "fireball", 120, 10);

		assertEquals(10, scheduler.getSpellCooldownRemaining(uuid, "fireball", 120));
		scheduler.clearEntity(uuid);
	}

	@Test
	void protectionIsClaimableAgainOnceTheWindowEnds() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertTrue(scheduler.acquireProtection(uuid, 10, 20));
		assertFalse(scheduler.acquireProtection(uuid, 29, 20));
		assertTrue(scheduler.acquireProtection(uuid, 30, 20));

		scheduler.clearEntity(uuid);
	}

	@Test
	void zeroTickProtectionIsImmediatelyReusable() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertTrue(scheduler.acquireProtection(uuid, 5, 0));
		assertTrue(scheduler.acquireProtection(uuid, 5, 0));

		scheduler.clearEntity(uuid);
	}

	@Test
	void thornsCooldownMatchesTheProtectionSemantics() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertTrue(scheduler.acquireThorns(uuid, 10, 20));
		assertFalse(scheduler.acquireThorns(uuid, 29, 20));
		assertTrue(scheduler.acquireThorns(uuid, 30, 20));
		assertTrue(scheduler.acquireThorns(uuid, 50, 0));
		assertTrue(scheduler.acquireThorns(uuid, 50, 0));

		scheduler.clearEntity(uuid);
	}

	@Test
	void arrowDeflectionAlwaysCooldownsForAtLeastOneTick() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertTrue(scheduler.acquireArrowDeflection(uuid, 10, 0));
		assertFalse(scheduler.acquireArrowDeflection(uuid, 10, 0));
		assertTrue(scheduler.acquireArrowDeflection(uuid, 11, 0));

		scheduler.clearEntity(uuid);
	}

	@Test
	void autoMoveSuppressionExpiresOnTime() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		scheduler.suppressAutoMove(uuid, 10, 20);
		assertTrue(scheduler.isAutoMoveSuppressed(uuid, 29));
		assertFalse(scheduler.isAutoMoveSuppressed(uuid, 30));

		scheduler.suppressAutoMove(uuid, 50, 0);
		assertFalse(scheduler.isAutoMoveSuppressed(uuid, 50));

		scheduler.clearEntity(uuid);
	}

	@Test
	void healingPreventionAccumulates() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertEquals(0.0F, scheduler.healingPrevention(uuid), 1e-6F);

		scheduler.addHealingPrevention(uuid, 0, 2.5F);
		scheduler.addHealingPrevention(uuid, 1, 1.5F);

		assertEquals(4.0F, scheduler.healingPrevention(uuid), 1e-6F);
		assertEquals(0.0F, scheduler.healingPrevention(UUID.randomUUID()), 1e-6F);

		scheduler.clearEntity(uuid);
	}

	@Test
	void flightIsGrantedWhenTheEntityCannotFly() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		final Scheduler.FlightUpdate first =
				scheduler.updateFlight(uuid, 0, 0.0, false);
		assertEquals(Scheduler.FlightAction.NONE, first.action());
		assertEquals(0.0, first.level(), 1e-9);

		final Scheduler.FlightUpdate granted =
				scheduler.updateFlight(uuid, 0, 1.0, false);
		assertEquals(Scheduler.FlightAction.GRANT, granted.action());
		assertEquals(1.0, granted.level(), 1e-9);

		final Scheduler.FlightUpdate steady =
				scheduler.updateFlight(uuid, 1, 1.0, true);
		assertEquals(Scheduler.FlightAction.NONE, steady.action());

		scheduler.clearEntity(uuid);
	}

	@Test
	void flightUpdatesWhenALevelGrantedByRelicsChanges() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		scheduler.updateFlight(uuid, 0, 1.0, false);

		final Scheduler.FlightUpdate update =
				scheduler.updateFlight(uuid, 1, 2.0, true);
		assertEquals(Scheduler.FlightAction.UPDATE, update.action());
		assertEquals(2.0, update.level(), 1e-9);

		scheduler.clearEntity(uuid);
	}

	@Test
	void flightIsRemovedWhenTheLevelDropsToZero() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		scheduler.updateFlight(uuid, 0, 2.0, false);

		final Scheduler.FlightUpdate update =
				scheduler.updateFlight(uuid, 1, 0.0, true);
		assertEquals(Scheduler.FlightAction.REMOVE, update.action());
		assertEquals(2.0, update.level(), 1e-9);

		scheduler.clearEntity(uuid);
	}

	@Test
	void vanillaFlightIsNotRemoved() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		final Scheduler.FlightUpdate granted =
				scheduler.updateFlight(uuid, 0, 1.0, true);
		assertEquals(Scheduler.FlightAction.NONE, granted.action());

		final Scheduler.FlightUpdate removed =
				scheduler.updateFlight(uuid, 1, 0.0, true);
		assertEquals(Scheduler.FlightAction.NONE, removed.action());
		assertEquals(0.0, removed.level(), 1e-9);

		scheduler.clearEntity(uuid);
	}

	@Test
	void clearEntityDropsAllState() {
		final UUID uuid = UUID.randomUUID();
		final Scheduler scheduler = Scheduler.INSTANCE();

		assertEquals("b", scheduler.selectSpell(uuid, SPELLS, 1, 0));
		scheduler.setSpellCooldown(uuid, "fireball", 100, 50);
		scheduler.acquireProtection(uuid, 10, 50);

		scheduler.clearEntity(uuid);

		assertEquals("a", scheduler.selectedSpell(uuid, SPELLS, 0));
		assertEquals(0, scheduler.getSpellCooldownRemaining(uuid, "fireball", 100));
		assertTrue(scheduler.acquireProtection(uuid, 10, 50));

		scheduler.clearEntity(uuid);
	}
}
