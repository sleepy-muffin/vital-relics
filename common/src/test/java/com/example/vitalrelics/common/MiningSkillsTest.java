package com.example.vitalrelics.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for tree felling and area mining selection.
 *
 * <p>The real adapter supplies the world; here a fake context records every
 * block that would be broken.</p>
 */
class MiningSkillsTest {

	private static final class FakeContext implements MiningSkills.Context {
		private final Set<MiningSkills.Pos> logs;
		private final List<MiningSkills.Pos> broken = new ArrayList<>();
		private final int validLimit;
		private int validCalls;
		private boolean breakSucceeds = true;

		FakeContext(final Set<MiningSkills.Pos> logs) {
			this(logs, Integer.MAX_VALUE);
		}

		FakeContext(final Set<MiningSkills.Pos> logs, final int validLimit) {
			this.logs = logs;
			this.validLimit = validLimit;
		}

		@Override
		public boolean valid() {
			return validCalls++ < validLimit;
		}

		@Override
		public boolean isLog(final MiningSkills.Pos pos) {
			return logs.contains(pos);
		}

		@Override
		public boolean breakBlock(final MiningSkills.Pos pos) {
			broken.add(pos);
			return breakSucceeds;
		}
	}

	private static final MiningSkills.Pos ORIGIN = new MiningSkills.Pos(0, 0, 0);

	@Test
	void treeModeBreaksConnectedLogsButNotTheOrigin() {
		final MiningSkills.Pos trunk = new MiningSkills.Pos(1, 0, 0);
		final MiningSkills.Pos branch = new MiningSkills.Pos(2, 0, 0);

		final Set<MiningSkills.Pos> logs = new HashSet<>();
		logs.add(ORIGIN);
		logs.add(trunk);
		logs.add(branch);

		final FakeContext context = new FakeContext(logs);

		MiningSkills.apply(context, ORIGIN, true, 0);

		assertTrue(context.broken.contains(trunk));
		assertTrue(context.broken.contains(branch));
		assertFalse(context.broken.contains(ORIGIN),
				"traversal must never break the origin itself");
		assertFalse(context.broken.contains(new MiningSkills.Pos(0, 1, 0)),
				"non-log neighbours must be skipped");
	}

	@Test
	void treeModeStopsWhenTheContextBecomesInvalid() {
		final MiningSkills.Pos trunk = new MiningSkills.Pos(1, 0, 0);
		final FakeContext context = new FakeContext(Set.of(ORIGIN, trunk), 2);

		MiningSkills.apply(context, ORIGIN, true, 0);

		assertEquals(List.of(trunk), context.broken);
	}

	@Test
	void areaMiningSkipsTheOriginAndBreaksNearestFirst() {
		final FakeContext context = new FakeContext(Set.of());

		MiningSkills.apply(context, ORIGIN, false, 2.0);

		assertFalse(context.broken.isEmpty());
		assertFalse(context.broken.contains(ORIGIN));

		for (final MiningSkills.Pos pos : context.broken)
			assertTrue(pos.distanceSquared(ORIGIN) <= 4.0 + 1e-9,
					"block outside the radius: " + pos);

		for (int i = 1; i < context.broken.size(); i++)
			assertTrue(
					context.broken.get(i - 1).distanceSquared(ORIGIN)
							<= context.broken.get(i).distanceSquared(ORIGIN),
					"blocks must be broken nearest-first");
	}

	@Test
	void areaMiningClampsTheRadiusToTheMaximum() {
		final FakeContext context = new FakeContext(Set.of());

		MiningSkills.apply(context, ORIGIN, false, 1000.0);

		assertEquals(MiningSkills.MAX_BLOCKS, context.broken.size());

		for (final MiningSkills.Pos pos : context.broken)
			assertTrue(pos.distanceSquared(ORIGIN)
							<= MiningSkills.MAX_AREA_RADIUS * MiningSkills.MAX_AREA_RADIUS,
					"radius was not clamped: " + pos);
	}

	@Test
	void areaMiningIgnoresNonPositiveAndNonFiniteRanges() {
		for (final double range : new double[]{
				0.0, -1.0, Double.NaN,
				Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {

			final FakeContext context = new FakeContext(Set.of());
			MiningSkills.apply(context, ORIGIN, false, range);

			assertTrue(context.broken.isEmpty(),
					"range " + range + " must not break any block");
		}
	}

	@Test
	void failedBreaksDoNotCountTowardsTheBlockLimit() {
		final FakeContext context = new FakeContext(Set.of());
		context.breakSucceeds = false;

		MiningSkills.apply(context, ORIGIN, false, MiningSkills.MAX_AREA_RADIUS);

		assertTrue(context.broken.size() > MiningSkills.MAX_BLOCKS,
				"failed breaks must not consume the block budget");
	}
}
