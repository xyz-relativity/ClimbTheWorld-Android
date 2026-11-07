package com.climbtheworld.app.augmentedreality.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * The order the list to align on shows the routes in: as their crag lists them, which is how they
 * stand along the wall, and the crags as their area lists them, so the user finds the route in
 * front of them as in a guidebook. The crags of the closest route's area come first, all of them
 * in the area's order, an area within it listing its own crags where the area lists it; then
 * those of the area of the closest route left, and so on, a crag of no area coming alone. The
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
	 * A crag or area relation around: its OSM ID, and the OSM IDs of its members in its order,
	 * its member nodes for a crag, its routes, and its member relations for an area, its crags
	 * and the areas within it.
	 */
	public static final class Site {
		final long id;
		final List<Long> members;

		public Site(long id, List<Long> members) {
			this.id = id;
			this.members = members;
		}
	}

	/**
	 * @param closestFirst the routes around, closest first
	 * @param crags        the crag relations around, see Site; a crag's index here is its
	 *                     Group.crag
	 * @param areas        the area relations around, see Site
	 * @param id           a route's OSM ID
	 * @return the routes, each once, in the order to list them
	 */
	public static <T> List<T> ordered(List<T> closestFirst, List<Site> crags, List<Site> areas,
	                                  ToLongFunction<T> id) {
		List<T> ordered = new ArrayList<>(closestFirst.size());
		for (Group<T> group : grouped(closestFirst, crags, areas, id)) {
			ordered.addAll(group.routes);
		}
		return ordered;
	}

	/**
	 * The routes in the order to list them, see ordered, by crag: each route with the first crag
	 * to list it, the crags in the order they come; a crag with no route left around has no group.
	 */
	public static <T> List<Group<T>> grouped(List<T> closestFirst, List<Site> crags,
	                                         List<Site> areas, ToLongFunction<T> id) {
		return new Grouping<>(closestFirst, crags, areas, id).groups();
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

	/**
	 * Works out grouped, once.
	 */
	private static final class Grouping<T> {
		private final List<T> closestFirst;
		private final List<Site> crags;
		private final List<Site> areas;
		private final ToLongFunction<T> id;
		// The routes around, by OSM ID.
		private final Map<Long, T> around = new HashMap<>();
		// Each crag's and area's index, by OSM ID.
		private final Map<Long, Integer> cragIndex = new HashMap<>();
		private final Map<Long, Integer> areaIndex = new HashMap<>();
		// The first crag listing each route, by the route's OSM ID.
		private final Map<Long, Integer> cragOfRoute = new HashMap<>();
		// The first area listing each crag and area, by its OSM ID.
		private final Map<Long, Integer> areaOf = new HashMap<>();
		private final Set<Long> listed = new HashSet<>();
		private final Set<Integer> listedCrags = new HashSet<>();
		private final Set<Integer> listedAreas = new HashSet<>();
		private final List<Group<T>> groups = new ArrayList<>();

		Grouping(List<T> closestFirst, List<Site> crags, List<Site> areas,
		         ToLongFunction<T> id) {
			this.closestFirst = closestFirst;
			this.crags = crags;
			this.areas = areas;
			this.id = id;
			for (T route : closestFirst) {
				around.putIfAbsent(id.applyAsLong(route), route);
			}
			for (int crag = 0; crag < crags.size(); crag++) {
				cragIndex.putIfAbsent(crags.get(crag).id, crag);
				for (Long member : crags.get(crag).members) {
					cragOfRoute.putIfAbsent(member, crag);
				}
			}
			for (int area = 0; area < areas.size(); area++) {
				areaIndex.putIfAbsent(areas.get(area).id, area);
				for (Long member : areas.get(area).members) {
					areaOf.putIfAbsent(member, area);
				}
			}
		}

		List<Group<T>> groups() {
			// The crags of the outermost area of the closest route left come next, or its crag
			// alone when it is in no area.
			for (T closest : closestFirst) {
				long routeId = id.applyAsLong(closest);
				Integer crag = cragOfRoute.get(routeId);
				if (listed.contains(routeId) || crag == null) {
					continue;
				}
				int area = outermostArea(crags.get(crag).id);
				if (area < 0) {
					addCrag(crag);
				} else {
					addArea(area);
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
		 * @return the index of the outermost area holding the crag or area, through the first
		 * area listing each, -1 for none; areas listing each other end at the last one not met
		 * yet
		 */
		private int outermostArea(long relationId) {
			int outermost = -1;
			Set<Integer> met = new HashSet<>();
			for (Integer area = areaOf.get(relationId); area != null && met.add(area);
			     area = areaOf.get(areas.get(area).id)) {
				outermost = area;
			}
			return outermost;
		}

		/**
		 * Adds the crags of the area, in its order, those of an area within it where it lists
		 * that area, each crag and area once.
		 */
		private void addArea(int area) {
			if (!listedAreas.add(area)) {
				return;
			}
			for (Long member : areas.get(area).members) {
				Integer crag = cragIndex.get(member);
				if (crag != null) {
					addCrag(crag);
				}
				Integer inner = areaIndex.get(member);
				if (inner != null) {
					addArea(inner);
				}
			}
		}

		/**
		 * Adds the group of the crag's routes around not listed yet, in its order, unless there
		 * are none, each crag once.
		 */
		private void addCrag(int crag) {
			if (!listedCrags.add(crag)) {
				return;
			}
			Group<T> group = new Group<>(crag);
			for (Long member : crags.get(crag).members) {
				T route = around.get(member);
				if (route != null && listed.add(member)) {
					group.routes.add(route);
				}
			}
			if (!group.routes.isEmpty()) {
				groups.add(group);
			}
		}
	}
}
