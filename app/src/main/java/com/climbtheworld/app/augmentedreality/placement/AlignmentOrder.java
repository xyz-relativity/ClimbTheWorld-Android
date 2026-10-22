package com.climbtheworld.app.augmentedreality.placement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * The order the list to align on shows the routes in: as their crag lists them, which is how they
 * stand along the wall, so the user finds the route in front of them as in a guidebook. The crag
 * of the closest route comes first, then the crag of the closest route left, and so on; the
 * routes of no crag come last, closest first.
 */
public final class AlignmentOrder {
	private AlignmentOrder() {
	}

	/**
	 * @param closestFirst the routes around, closest first
	 * @param crags        per crag relation around, the OSM IDs of its member nodes in the
	 *                     relation's order
	 * @param id           a route's OSM ID
	 * @return the routes, each once, in the order to list them
	 */
	public static <T> List<T> ordered(List<T> closestFirst, List<List<Long>> crags,
	                                  ToLongFunction<T> id) {
		List<T> ordered = new ArrayList<>(closestFirst.size());
		Set<Long> listed = new HashSet<>();
		// The first crag listing the closest route left lists its routes next.
		for (T closest : closestFirst) {
			if (listed.contains(id.applyAsLong(closest))) {
				continue;
			}
			for (List<Long> crag : crags) {
				if (!crag.contains(id.applyAsLong(closest))) {
					continue;
				}
				for (Long member : crag) {
					for (T route : closestFirst) {
						if (id.applyAsLong(route) == member && listed.add(member)) {
							ordered.add(route);
						}
					}
				}
				break;
			}
		}
		// Then the routes of no crag around.
		for (T route : closestFirst) {
			if (listed.add(id.applyAsLong(route))) {
				ordered.add(route);
			}
		}
		return ordered;
	}
}
