package com.climbtheworld.app.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PixelBlurTest {
	private static final int OPAQUE_RED = 0xffff0000;
	private static final int OPAQUE_BLUE = 0xff0000ff;

	private static int alpha(int pixel) {
		return pixel >>> 24;
	}

	private static int[] filled(int width, int height, int pixel) {
		int[] pixels = new int[width * height];
		java.util.Arrays.fill(pixels, pixel);
		return pixels;
	}

	@Test
	public void leavesAUniformImageAsItIs() {
		int[] pixels = filled(7, 5, OPAQUE_RED);
		PixelBlur.blur(pixels, 7, 5, 2);
		assertArrayEquals(filled(7, 5, OPAQUE_RED), pixels);
	}

	@Test
	public void doesNothingForNoRadius() {
		int[] pixels = new int[9];
		pixels[4] = OPAQUE_RED;
		int[] before = pixels.clone();
		PixelBlur.blur(pixels, 3, 3, 0);
		assertArrayEquals(before, pixels);
	}

	@Test
	public void spreadsAPixelOutEvenlyAndKeepsItsColour() {
		int[] pixels = new int[9 * 9];
		pixels[4 * 9 + 4] = OPAQUE_RED;
		PixelBlur.blur(pixels, 9, 9, 1);

		int centre = pixels[4 * 9 + 4];
		int left = pixels[4 * 9 + 3];
		int right = pixels[4 * 9 + 5];
		int up = pixels[3 * 9 + 4];
		int far = pixels[4 * 9 + 1];
		assertTrue(alpha(centre) > alpha(left));
		assertEquals(alpha(left), alpha(right));
		assertEquals(alpha(left), alpha(up));
		assertTrue(alpha(left) > alpha(far));
		assertEquals(0, alpha(pixels[0]));
		// Red stays red, however faint.
		assertEquals(0xff0000, left & 0xffffff);
		assertEquals(0xff0000, far & 0xffffff);
	}

	@Test
	public void keepsTheAmountOfInk() {
		int[] pixels = new int[11 * 11];
		pixels[5 * 11 + 5] = OPAQUE_RED;
		pixels[5 * 11 + 6] = OPAQUE_RED;
		PixelBlur.blur(pixels, 11, 11, 1);

		int ink = 0;
		for (int pixel : pixels) {
			ink += alpha(pixel);
		}
		// Two opaque pixels' worth, give or take the rounding of each.
		assertEquals(2 * 255, ink, 11 * 11 / 2.0);
	}

	@Test
	public void clearPixelsDoNotDarkenTheColour() {
		// Red next to clear: the edge stays red, only fainter, not a darker red.
		int[] pixels = new int[5 * 1];
		pixels[0] = OPAQUE_RED;
		pixels[1] = OPAQUE_RED;
		PixelBlur.blur(pixels, 5, 1, 1);

		for (int x = 0; x < 4; x++) {
			if (alpha(pixels[x]) > 0) {
				assertEquals(0xff0000, pixels[x] & 0xffffff);
			}
		}
	}

	@Test
	public void reachesAsFarAsItSpreads() {
		assertEquals(0, PixelBlur.reach(0));
		assertEquals(3, PixelBlur.reach(1));

		// A pixel in the middle of a row spreads as far as the reach and no further.
		int reach = PixelBlur.reach(2);
		int width = 2 * reach + 3;
		int[] pixels = new int[width];
		pixels[reach + 1] = OPAQUE_RED;
		PixelBlur.blur(pixels, width, 1, 2);
		assertTrue(alpha(pixels[1]) > 0);
		assertEquals(0, alpha(pixels[0]));
		assertTrue(alpha(pixels[width - 2]) > 0);
		assertEquals(0, alpha(pixels[width - 1]));
	}

	@Test
	public void mixesColoursAcrossAnEdge() {
		int[] pixels = new int[12];
		for (int x = 0; x < 12; x++) {
			pixels[x] = x < 6 ? OPAQUE_RED : OPAQUE_BLUE;
		}
		PixelBlur.blur(pixels, 12, 1, 1);

		int atEdge = pixels[5];
		assertEquals(255, alpha(atEdge));
		assertTrue(((atEdge >> 16) & 0xff) > 0);
		assertTrue((atEdge & 0xff) > 0);
		// Three passes of radius 1 reach three pixels: further away nothing changes, and
		// nothing bleeds in from beyond the ends either.
		assertEquals(OPAQUE_RED, pixels[0]);
		assertEquals(OPAQUE_RED, pixels[2]);
		assertEquals(OPAQUE_BLUE, pixels[9]);
		assertEquals(OPAQUE_BLUE, pixels[11]);
	}
}
