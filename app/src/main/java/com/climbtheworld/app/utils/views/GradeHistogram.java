package com.climbtheworld.app.utils.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The bars of a histogram of route counts per grade. Its axis is the whole grade scale, easiest
 * to hardest, whatever the routes, so that every histogram looks the same: a grade is always at
 * the same place. A bar is as high as its count against a scale the histograms compared share.
 * The routes matching the display filters fill its bottom, the rest stack on them.
 */
public final class GradeHistogram {
	/**
	 * A grade's bar, in pixels from the histogram's top left.
	 *
	 * @param top         the top of the bar, all its routes
	 * @param matchingTop the top of the routes matching the display filters, from the bottom;
	 *                    the bottom itself when none matches
	 */
	public record Bar(int grade, float left, float right, float top, float matchingTop) {
	}

	private GradeHistogram() {
	}

	/**
	 * @param gradeCounts         route count per grade index; unknown grades (negative) and
	 *                            grades past the scale are left out
	 * @param matchingGradeCounts the same for the routes matching the display filters, at most
	 *                            gradeCounts'; the same map while no filter is on
	 * @param gradeCount          how many grades the axis has
	 * @param scale               route count of a full height bar
	 * @param minWidth            the least width of a bar, so that it shows on a narrow axis
	 * @param minHeight           the least height of a bar or of its matching routes, so that
	 *                            a lone route shows against a large scale
	 * @return the bars of the grades with routes, easiest first
	 */
	public static List<Bar> bars(Map<Integer, Integer> gradeCounts,
	                             Map<Integer, Integer> matchingGradeCounts, int gradeCount,
	                             int scale, float width, float height, float minWidth,
	                             float minHeight) {
		List<Bar> result = new ArrayList<>();
		for (int grade = 0; grade < gradeCount; grade++) {
			int count = countOf(gradeCounts, grade);
			if (count <= 0) {
				continue;
			}
			float start = grade * width / gradeCount;
			float right = Math.min(width,
					Math.max((grade + 1) * width / gradeCount, start + minWidth));
			float left = Math.max(0, Math.min(start, right - minWidth));
			float top = height - barHeight(count, scale, height, minHeight);
			int matching = Math.min(countOf(matchingGradeCounts, grade), count);
			float matchingTop = matching <= 0 ? height
					: Math.max(top, height - barHeight(matching, scale, height, minHeight));
			result.add(new Bar(grade, left, right, top, matchingTop));
		}
		return result;
	}

	/**
	 * @param gradeCounts the route counts per grade index of the histograms that share a scale
	 * @param gradeCount  how many grades the axis has
	 * @return the largest count of one grade on the axis among them, at least 1
	 */
	public static int largestCount(Collection<? extends Map<Integer, Integer>> gradeCounts,
	                               int gradeCount) {
		int result = 1;
		for (Map<Integer, Integer> counts : gradeCounts) {
			for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
				if (entry.getKey() >= 0 && entry.getKey() < gradeCount) {
					result = Math.max(result, entry.getValue());
				}
			}
		}
		return result;
	}

	private static int countOf(Map<Integer, Integer> gradeCounts, int grade) {
		Integer result = gradeCounts.get(grade);
		return result != null ? result : 0;
	}

	private static float barHeight(int count, int scale, float height, float minHeight) {
		return Math.min(height, Math.max(minHeight, height * count / Math.max(scale, 1)));
	}
}
