package com.climbtheworld.app.utils.views;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class PinnedHeaderTest {
	private static final int HEIGHT = 60;

	private static PinnedHeader pin(int... tops) {
		int[] heights = new int[tops.length];
		Arrays.fill(heights, HEIGHT);
		return PinnedHeader.of(tops, heights);
	}

	@Test
	public void nothingIsPinnedBeforeTheFirstHeaderReachesTheTop() {
		// The list's top margin: the first header still shows in place.
		assertEquals(-1, pin(2, 800, 1600).index);
		assertEquals(-1, pin().index);
	}

	@Test
	public void theFirstHeaderIsPinnedOnceAtTheTop() {
		PinnedHeader atTop = pin(0, 800, 1600);
		assertEquals(0, atTop.index);
		assertEquals(0, atTop.offset);

		PinnedHeader scrolledPast = pin(-300, 500, 1300);
		assertEquals(0, scrolledPast.index);
		assertEquals(0, scrolledPast.offset);
	}

	@Test
	public void theNextHeaderPushesThePinnedOneOut() {
		// Just touching it: not pushed yet.
		assertEquals(0, pin(-500, HEIGHT, 900).offset);

		// 20 px into it: drawn 20 px above the top, its bottom on the next header's top.
		PinnedHeader pushed = pin(-520, 40, 880);
		assertEquals(0, pushed.index);
		assertEquals(-20, pushed.offset);

		// Pushed all the way out, as the next one reaches the top.
		PinnedHeader almostOut = pin(-559, 1, 841);
		assertEquals(0, almostOut.index);
		assertEquals(-59, almostOut.offset);
	}

	@Test
	public void theNextHeaderTakesItsPlaceAtTheTop() {
		PinnedHeader next = pin(-560, 0, 840);
		assertEquals(1, next.index);
		assertEquals(0, next.offset);
	}

	@Test
	public void theLastHeaderStaysPinned() {
		PinnedHeader last = pin(-2000, -1200, -400);
		assertEquals(2, last.index);
		assertEquals(0, last.offset);
	}

	@Test
	public void thePushFollowsThePinnedHeadersOwnHeight() {
		// A taller header, its title over two lines: pushed once the next is within its height.
		PinnedHeader tall = PinnedHeader.of(new int[]{-500, 90}, new int[]{100, 60});
		assertEquals(0, tall.index);
		assertEquals(-10, tall.offset);
	}
}
