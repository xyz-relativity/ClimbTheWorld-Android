package com.climbtheworld.app.augmentedreality.placement;

import com.climbtheworld.app.storage.logbook.LogBookFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Which routes the list to align on shows, for what the user typed in its quick filter: the
 * routes whose name holds the text, ignoring case and accents, so "ete" finds "Été", as many as
 * the list holds around the closest of them, which the list opens on, as the route in front of
 * the user is most likely the closest or one of its neighbours along the wall; and past those,
 * the routes aligned on whose name holds it, however many, for their alignments to be deleted.
 */
public final class AlignmentChoices {
	private AlignmentChoices() {
	}

	/**
	 * @param ordered  the routes around, in the order to list them, see AlignmentOrder, the
	 *                 routes aligned on among them
	 * @param filter   what the user typed, empty for any route
	 * @param distance how far a route is from the user
	 * @param max      how many routes the list holds, routes aligned on past them left aside
	 * @return the routes to list, in the same order
	 */
	public static <T> List<T> listed(List<T> ordered, String filter, Function<T, String> name,
	                                 Predicate<T> aligned, ToDoubleFunction<T> distance,
	                                 int max) {
		String text = LogBookFilter.normalize(filter.trim());
		List<T> matching = new ArrayList<>();
		for (T route : ordered) {
			if (LogBookFilter.normalize(name.apply(route)).contains(text)) {
				matching.add(route);
			}
		}
		// As many routes before the closest as after it, the rest on the other side at the
		// list's ends.
		int first = Math.max(0,
				Math.min(closest(matching, distance) - max / 2, matching.size() - max));
		List<T> listed = new ArrayList<>();
		for (int i = 0; i < matching.size(); i++) {
			T route = matching.get(i);
			if ((i >= first && i < first + max) || aligned.test(route)) {
				listed.add(route);
			}
		}
		return listed;
	}

	/**
	 * @param distance how far a route is from the user
	 * @return the index of the closest route, the first of them when as close, -1 for none
	 */
	public static <T> int closest(List<T> routes, ToDoubleFunction<T> distance) {
		int closest = -1;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (int i = 0; i < routes.size(); i++) {
			double meters = distance.applyAsDouble(routes.get(i));
			if (closest < 0 || meters < closestDistance) {
				closest = i;
				closestDistance = meters;
			}
		}
		return closest;
	}
}
