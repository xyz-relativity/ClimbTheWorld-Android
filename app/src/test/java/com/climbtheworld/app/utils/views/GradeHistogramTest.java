package com.climbtheworld.app.utils.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class GradeHistogramTest {
	private static final float DELTA = 0.0001f;

	private static Map<Integer, Integer> counts(int... gradeAndCount) {
		Map<Integer, Integer> result = new TreeMap<>();
		for (int i = 0; i < gradeAndCount.length; i += 2) {
			result.put(gradeAndCount[i], gradeAndCount[i + 1]);
		}
		return result;
	}

	@Test
	public void aGradeIsAlwaysAtTheSamePlaceOnTheAxis() {
		// 10 grades over 100 px: grade 3 is at 30 to 40 px, whatever the other grades.
		List<GradeHistogram.Bar> alone = GradeHistogram.bars(counts(3, 1), counts(3, 1), 10, 4,
				100, 40, 1, 1);
		List<GradeHistogram.Bar> withOthers = GradeHistogram.bars(counts(0, 2, 3, 1, 9, 4),
				counts(0, 2, 3, 1, 9, 4), 10, 4, 100, 40, 1, 1);

		assertEquals(1, alone.size());
		assertEquals(3, alone.get(0).grade());
		assertEquals(30, alone.get(0).left(), DELTA);
		assertEquals(40, alone.get(0).right(), DELTA);
		assertEquals(3, withOthers.size());
		assertEquals(30, withOthers.get(1).left(), DELTA);
		assertEquals(40, withOthers.get(1).right(), DELTA);
		assertEquals(90, withOthers.get(2).left(), DELTA);
		assertEquals(100, withOthers.get(2).right(), DELTA);
	}

	@Test
	public void aBarIsAsHighAsItsCountAgainstTheScale() {
		List<GradeHistogram.Bar> bars = GradeHistogram.bars(counts(1, 4, 2, 1), counts(1, 4, 2, 1),
				10, 4, 100, 40, 1, 1);

		// Full height for the scale's count, a quarter of it for a quarter of it.
		assertEquals(0, bars.get(0).top(), DELTA);
		assertEquals(30, bars.get(1).top(), DELTA);
		// No filter: all the routes match.
		assertEquals(bars.get(0).top(), bars.get(0).matchingTop(), DELTA);
		assertEquals(bars.get(1).top(), bars.get(1).matchingTop(), DELTA);
	}

	@Test
	public void theMatchingRoutesFillTheBottomOfTheBar() {
		List<GradeHistogram.Bar> bars = GradeHistogram.bars(counts(1, 4, 2, 2), counts(1, 1),
				10, 4, 100, 40, 1, 1);

		assertEquals(0, bars.get(0).top(), DELTA);
		assertEquals(30, bars.get(0).matchingTop(), DELTA);
		// None of the grade matches: the matching part is empty, at the bottom.
		assertEquals(20, bars.get(1).top(), DELTA);
		assertEquals(40, bars.get(1).matchingTop(), DELTA);
	}

	@Test
	public void unknownGradesAndGradesPastTheScaleAreLeftOut() {
		List<GradeHistogram.Bar> bars = GradeHistogram.bars(counts(-1, 5, 2, 1, 10, 3),
				counts(-1, 5, 2, 1, 10, 3), 10, 5, 100, 40, 1, 1);

		assertEquals(1, bars.size());
		assertEquals(2, bars.get(0).grade());
	}

	@Test
	public void aLoneRouteShowsAgainstALargeScaleOnANarrowAxis() {
		// 46 grades over 23 px: half a pixel each.
		List<GradeHistogram.Bar> bars = GradeHistogram.bars(counts(45, 1), counts(45, 1), 46,
				1000, 23, 18, 1, 2);

		GradeHistogram.Bar bar = bars.get(0);
		assertEquals(1, bar.right() - bar.left(), DELTA);
		assertTrue(bar.right() <= 23);
		assertEquals(16, bar.top(), DELTA);
		assertEquals(16, bar.matchingTop(), DELTA);
	}

	@Test
	public void theScaleIsTheLargestCountOfOneGradeOnTheAxis() {
		assertEquals(7, GradeHistogram.largestCount(Arrays.asList(counts(1, 3, 2, 7),
				counts(-1, 50, 4, 5), counts(10, 99)), 10));
		// Never 0, as it divides.
		assertEquals(1, GradeHistogram.largestCount(Collections.singletonList(counts()), 10));
	}
}
