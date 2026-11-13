package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AnchorPositionsTest {
	private static Map<String, double[]> positions(Object... keysAndPoints) {
		Map<String, double[]> positions = new HashMap<>();
		for (int i = 0; i < keysAndPoints.length; i += 2) {
			positions.put((String) keysAndPoints[i], (double[]) keysAndPoints[i + 1]);
		}
		return positions;
	}

	@Test
	public void anchorsReadAgainAtTheSamePointsAreTheSame() {
		assertTrue(AnchorPositions.same(
				positions("route:1", new double[]{1, 2, 3}, "pin", new double[]{-4, 0.5, 6}),
				positions("pin", new double[]{-4, 0.5, 6}, "route:1", new double[]{1, 2, 3})));
	}

	@Test
	public void noAnchorsAreTheSameAsNone() {
		assertTrue(AnchorPositions.same(Collections.emptyMap(), new HashMap<>()));
	}

	@Test
	public void anAnchorMovedHoweverLittleIsNotTheSame() {
		assertFalse(AnchorPositions.same(
				positions("route:1", new double[]{1, 2, 3}, "route:2", new double[]{4, 5, 6}),
				positions("route:1", new double[]{1, 2, 3},
						"route:2", new double[]{4, Math.nextUp(5.0), 6})));
	}

	@Test
	public void anAnchorNoLongerTrackedIsNotTheSame() {
		assertFalse(AnchorPositions.same(
				positions("route:1", new double[]{1, 2, 3}, "route:2", new double[]{4, 5, 6}),
				positions("route:1", new double[]{1, 2, 3})));
		assertFalse(AnchorPositions.same(positions("route:1", new double[]{1, 2, 3}),
				Collections.emptyMap()));
	}

	@Test
	public void anAnchorTrackedAgainIsNotTheSame() {
		assertFalse(AnchorPositions.same(Collections.emptyMap(),
				positions("route:1", new double[]{1, 2, 3})));
	}

	@Test
	public void anotherAnchorAtTheSamePointIsNotTheSame() {
		assertFalse(AnchorPositions.same(positions("route:1", new double[]{1, 2, 3}),
				positions("route:2", new double[]{1, 2, 3})));
	}
}
