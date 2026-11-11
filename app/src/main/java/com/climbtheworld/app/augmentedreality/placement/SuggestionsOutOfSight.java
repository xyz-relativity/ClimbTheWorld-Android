package com.climbtheworld.app.augmentedreality.placement;

/**
 * Which of the suggested routes of the list to align on are out of sight on each side of it, as
 * the strip is scrolled, for a button on that side to say how many more there are, and to go to
 * the nearest of them. A route is out of sight once its middle is, as most of its card then is.
 */
public final class SuggestionsOutOfSight {
	// How many suggested routes are out of sight before the part of the strip in sight.
	public final int before;
	// How many are after it.
	public final int after;
	// Which of them is the nearest on each side, as the spans have them, -1 for none.
	public final int nearestBefore;
	public final int nearestAfter;

	private SuggestionsOutOfSight(int before, int after, int nearestBefore, int nearestAfter) {
		this.before = before;
		this.after = after;
		this.nearestBefore = nearestBefore;
		this.nearestAfter = nearestAfter;
	}

	/**
	 * @param spans each suggested route's start and end along the strip, in pairs
	 * @param from  where the part of the strip in sight starts, in the spans' unit
	 * @param to    where it ends
	 */
	public static SuggestionsOutOfSight of(float[] spans, float from, float to) {
		int before = 0;
		int after = 0;
		int nearestBefore = -1;
		int nearestAfter = -1;
		float nearestBeforeMiddle = Float.NEGATIVE_INFINITY;
		float nearestAfterMiddle = Float.POSITIVE_INFINITY;
		for (int i = 0; i + 1 < spans.length; i += 2) {
			float middle = (spans[i] + spans[i + 1]) / 2;
			if (middle < from) {
				before++;
				if (middle > nearestBeforeMiddle) {
					nearestBefore = i / 2;
					nearestBeforeMiddle = middle;
				}
			} else if (middle > to) {
				after++;
				if (middle < nearestAfterMiddle) {
					nearestAfter = i / 2;
					nearestAfterMiddle = middle;
				}
			}
		}
		return new SuggestionsOutOfSight(before, after, nearestBefore, nearestAfter);
	}
}
