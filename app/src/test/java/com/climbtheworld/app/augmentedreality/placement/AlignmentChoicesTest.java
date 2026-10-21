package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * AlignmentChoices.
 */
public class AlignmentChoicesTest {
	private static final List<String> CLOSEST_FIRST = Arrays.asList("Été indien", "La Fissure",
			"Le Pilier", "Les Étés", "Fissure des Rêves");

	private static List<String> listed(String filter, Set<String> aligned, int max) {
		return AlignmentChoices.listed(CLOSEST_FIRST, filter, Function.identity(),
				aligned::contains, max);
	}

	@Test
	public void anEmptyFilterListsTheClosest() {
		assertEquals(Arrays.asList("Été indien", "La Fissure", "Le Pilier"),
				listed("", Set.of(), 3));
	}

	@Test
	public void theFilterFindsNamesIgnoringCaseAndAccents() {
		assertEquals(Arrays.asList("Été indien", "Les Étés"), listed(" ETE ", Set.of(), 5));
		assertEquals(Arrays.asList("Fissure des Rêves"), listed("reve", Set.of(), 5));
	}

	@Test
	public void theFilterLooksPastTheClosestTheListHolds() {
		// The second match is past the first two routes.
		assertEquals(Arrays.asList("La Fissure", "Fissure des Rêves"),
				listed("fissure", Set.of(), 2));
	}

	@Test
	public void routesAlignedOnAreListedPastTheLimit() {
		assertEquals(Arrays.asList("Été indien", "La Fissure", "Fissure des Rêves"),
				listed("", Set.of("Fissure des Rêves"), 2));
	}

	@Test
	public void routesAlignedOnAreFilteredToo() {
		assertEquals(Arrays.asList("Le Pilier"), listed("pilier", Set.of("Fissure des Rêves"), 2));
	}

	@Test
	public void routesAlignedOnCountTowardTheLimitWhenAmongTheClosest() {
		assertEquals(Arrays.asList("Été indien", "La Fissure"),
				listed("", Set.of("Été indien"), 2));
	}
}
