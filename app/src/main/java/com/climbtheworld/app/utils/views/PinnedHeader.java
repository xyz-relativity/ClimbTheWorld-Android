package com.climbtheworld.app.utils.views;

/**
 * Which header a scrolling list pins at its top, and how far up the next header pushes it, from
 * where the headers are: the arithmetic of {@link StickyHeaders}, apart so it is tested without
 * Android.
 */
final class PinnedHeader {
	static final PinnedHeader NONE = new PinnedHeader(-1, 0);

	/** The index of the pinned header, -1 while the first one is not scrolled up to the top. */
	final int index;
	/**
	 * How far above the top to draw it, 0 or less: by how much the next header, coming up
	 * under it, overlaps it, so the next header pushes it out and takes its place.
	 */
	final int offset;

	private PinnedHeader(int index, int offset) {
		this.index = index;
		this.offset = offset;
	}

	/**
	 * @param tops    the top of each header, in their order down the list, from the top of the
	 *                list's visible area: 0 at the top, less once scrolled past it
	 * @param heights the height of each header
	 */
	static PinnedHeader of(int[] tops, int[] heights) {
		// The last header scrolled up to the top or past it.
		int index = -1;
		for (int i = 0; i < tops.length && tops[i] <= 0; i++) {
			index = i;
		}
		if (index < 0) {
			return NONE;
		}

		int offset = 0;
		if (index + 1 < tops.length) {
			offset = Math.min(0, tops[index + 1] - heights[index]);
		}
		return new PinnedHeader(index, offset);
	}
}
