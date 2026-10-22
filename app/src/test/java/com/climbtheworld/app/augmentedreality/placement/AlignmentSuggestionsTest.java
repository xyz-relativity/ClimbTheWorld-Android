package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AlignmentSuggestions.
 */
public class AlignmentSuggestionsTest {
	// Routes along a wall running east, by how far east of the user they are.
	private static final Map<String, double[]> AT = Map.of(
			"here", new double[]{0, 0},
			"near", new double[]{5, 0},
			"middle", new double[]{12, 0},
			"far", new double[]{20, 0},
			"beyond", new double[]{40, 0});

	private static List<String> suggested(List<String> candidates, List<double[]> aligned) {
		return AlignmentSuggestions.suggested(candidates, AT::get, aligned);
	}

	@Test
	public void suggestsNothingUntilARouteIsAlignedOn() {
		assertTrue(suggested(Arrays.asList("near", "middle", "far"), List.of()).isEmpty());
	}

	@Test
	public void suggestsTheRoutesFarthestFromTheOneAlignedOnWithinAim() {
		// "near" is too close to set the heading, "beyond" too far to aim at.
		assertEquals(Arrays.asList("far", "middle"),
				suggested(Arrays.asList("near", "middle", "far", "beyond"), List.of(AT.get("here"))));
	}

	@Test
	public void suggestsAtMostThree() {
		Map<String, double[]> at = Map.of("a", new double[]{12, 0}, "b", new double[]{14, 0},
				"c", new double[]{16, 0}, "d", new double[]{18, 0});
		assertEquals(Arrays.asList("d", "c", "b"), AlignmentSuggestions.suggested(
				Arrays.asList("a", "b", "c", "d"), at::get, List.of(AT.get("here"))));
	}

	@Test
	public void weighsACandidateWithAllTheRoutesAlignedOn() {
		// Between two routes aligned on, a route in line with them adds nothing to the heading;
		// one off to the side does.
		Map<String, double[]> at = Map.of("in line", new double[]{6, 0},
				"aside", new double[]{6, 10});
		assertEquals(Arrays.asList("aside", "in line"), AlignmentSuggestions.suggested(
				Arrays.asList("in line", "aside"), at::get,
				List.of(AT.get("here"), AT.get("middle"))));
	}
}
