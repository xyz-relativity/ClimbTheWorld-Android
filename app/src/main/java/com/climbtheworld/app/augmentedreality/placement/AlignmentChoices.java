package com.climbtheworld.app.augmentedreality.placement;

import com.climbtheworld.app.storage.logbook.LogBookFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which routes the list to align on shows, for what the user typed in its quick filter: the
 * closest whose name holds the text, ignoring case and accents, so "ete" finds "Été", as many as
 * the list holds; and past those, the routes aligned on whose name holds it, however many, for
 * their alignments to be deleted.
 */
public final class AlignmentChoices {
	private AlignmentChoices() {
	}

	/**
	 * @param closestFirst the routes around, closest first, the routes aligned on among them
	 * @param filter       what the user typed, empty for any route
	 * @param max          how many routes the list holds, routes aligned on past them left aside
	 * @return the routes to list, closest first
	 */
	public static <T> List<T> listed(List<T> closestFirst, String filter, Function<T, String> name,
	                                 Predicate<T> aligned, int max) {
		String text = LogBookFilter.normalize(filter.trim());
		List<T> listed = new ArrayList<>();
		for (T route : closestFirst) {
			if (!LogBookFilter.normalize(name.apply(route)).contains(text)) {
				continue;
			}
			if (listed.size() < max || aligned.test(route)) {
				listed.add(route);
			}
		}
		return listed;
	}
}
