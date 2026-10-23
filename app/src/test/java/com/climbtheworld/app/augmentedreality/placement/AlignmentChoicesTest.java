package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * AlignmentChoices.
 */
public class AlignmentChoicesTest {
	private static final List<String> CLOSEST_FIRST = Arrays.asList("Été indien", "La Fissure",
			"Le Pilier", "Les Étés", "Fissure des Rêves");
	// Along the wall, the user in front of "D".
	private static final List<String> BY_CRAG = Arrays.asList("A", "B", "C", "D", "E", "F", "G");

	private static List<String> listed(String filter, Set<String> aligned, int max) {
		return AlignmentChoices.listed(CLOSEST_FIRST, filter, Function.identity(),
				aligned::contains, CLOSEST_FIRST::indexOf, max);
	}

	private static List<String> listedAround(String closest, Set<String> aligned, int max) {
		return AlignmentChoices.listed(BY_CRAG, "", Function.identity(), aligned::contains,
				route -> Math.abs(BY_CRAG.indexOf(route) - BY_CRAG.indexOf(closest)), max);
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

	@Test
	public void theListHoldsTheRoutesAroundTheClosest() {
		assertEquals(Arrays.asList("B", "C", "D", "E", "F"), listedAround("D", Set.of(), 5));
		// One more before it than after, when the list holds an even number.
		assertEquals(Arrays.asList("B", "C", "D", "E"), listedAround("D", Set.of(), 4));
	}

	@Test
	public void theListHoldsMoreOnTheOtherSideAtItsEnds() {
		assertEquals(Arrays.asList("A", "B", "C", "D"), listedAround("B", Set.of(), 4));
		assertEquals(Arrays.asList("D", "E", "F", "G"), listedAround("G", Set.of(), 4));
	}

	@Test
	public void routesAlignedOnBeforeTheClosestAreListedPastTheLimit() {
		assertEquals(Arrays.asList("A", "D", "E", "F"), listedAround("E", Set.of("A"), 3));
	}

	@Test
	public void theListIsAroundTheClosestMatch() {
		// "Fissure des Rêves" is the closest route, and of the routes whose name holds "fissure".
		assertEquals(Arrays.asList("Fissure des Rêves"),
				AlignmentChoices.listed(CLOSEST_FIRST, "fissure", Function.identity(),
						route -> false, route -> route.startsWith("Fissure") ? 0 : 1, 1));
	}

	@Test
	public void theClosestIsTheFirstOfThoseAsClose() {
		assertEquals(1, AlignmentChoices.closest(Arrays.asList(3.0, 1.0, 2.0, 1.0),
				Double::doubleValue));
		assertEquals(-1, AlignmentChoices.closest(Collections.<Double>emptyList(),
				Double::doubleValue));
	}
}
