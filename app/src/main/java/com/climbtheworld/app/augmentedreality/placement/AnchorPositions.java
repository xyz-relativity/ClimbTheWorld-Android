package com.climbtheworld.app.augmentedreality.placement;

import java.util.Arrays;
import java.util.Map;

/**
 * Where the anchors of an A.R. session are in its world, by key, as read on each of its frames.
 */
public final class AnchorPositions {
	private AnchorPositions() {
	}

	/**
	 * @return whether the anchors are where they were: the same ones, each at the very same
	 * point, so that what was worked out from where they were still holds
	 */
	public static boolean same(Map<String, double[]> before, Map<String, double[]> now) {
		if (before.size() != now.size()) {
			return false;
		}
		for (Map.Entry<String, double[]> anchor : now.entrySet()) {
			// As many, so that all those of now being among those before leaves none out.
			if (!Arrays.equals(before.get(anchor.getKey()), anchor.getValue())) {
				return false;
			}
		}
		return true;
	}
}
