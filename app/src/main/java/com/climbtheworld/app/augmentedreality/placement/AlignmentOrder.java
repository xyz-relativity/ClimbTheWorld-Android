package com.climbtheworld.app.augmentedreality.placement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * The order the list to align on shows the routes in: as their crag lists them, which is how they
 * stand along the wall, so the user finds the route in front of them as in a guidebook. The crag
 * of the closest route comes first, then the crag of the closest route left, and so on; the
 * routes of no crag come last, closest first. The list shows each crag's routes together, under
 * its name, see Group.
 */
public final class AlignmentOrder {
	/** The crag of the routes of no crag around. */
	public static final int NO_CRAG = -1;

	private AlignmentOrder() {
	}

	/**
	 * A crag's routes in its order, or the routes of no crag around, closest first.
	 */
	public static final class Group<T> {
		/** The crag's index among the crags around, NO_CRAG for the routes of none. */
		public final int crag;
		public final List<T> routes = new ArrayList<>();

		Group(int crag) {
			this.crag = crag;
		}
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
		for (Group<T> group : grouped(closestFirst, crags, id)) {
			ordered.addAll(group.routes);
		}
		return ordered;
	}

	/**
	 * The routes in the order to list them, see ordered, by crag: each route with the first crag
	 * to list it, the crags in the order they come.
	 */
	public static <T> List<Group<T>> grouped(List<T> closestFirst, List<List<Long>> crags,
	                                         ToLongFunction<T> id) {
		List<Group<T>> groups = new ArrayList<>();
		Set<Long> listed = new HashSet<>();
		// The first crag listing the closest route left lists its routes next.
		for (T closest : closestFirst) {
			if (listed.contains(id.applyAsLong(closest))) {
				continue;
			}
			for (int crag = 0; crag < crags.size(); crag++) {
				if (!crags.get(crag).contains(id.applyAsLong(closest))) {
					continue;
				}
				Group<T> group = new Group<>(crag);
				for (Long member : crags.get(crag)) {
					for (T route : closestFirst) {
						if (id.applyAsLong(route) == member && listed.add(member)) {
							group.routes.add(route);
						}
					}
				}
				groups.add(group);
				break;
			}
		}
		// Then the routes of no crag around.
		Group<T> none = new Group<>(NO_CRAG);
		for (T route : closestFirst) {
			if (listed.add(id.applyAsLong(route))) {
				none.routes.add(route);
			}
		}
		if (!none.routes.isEmpty()) {
			groups.add(none);
		}
		return groups;
	}

	/**
	 * Groups again routes in the order of grouped, some of them left out, as the quick filter
	 * does.
	 *
	 * @param crag the crag a route was grouped with, see Group.crag
	 */
	public static <T> List<Group<T>> regrouped(List<T> ordered, ToIntFunction<T> crag) {
		List<Group<T>> groups = new ArrayList<>();
		for (T route : ordered) {
			int routeCrag = crag.applyAsInt(route);
			if (groups.isEmpty() || groups.get(groups.size() - 1).crag != routeCrag) {
				groups.add(new Group<>(routeCrag));
			}
			groups.get(groups.size() - 1).routes.add(route);
		}
		return groups;
	}
}
