package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Every view but the member cards uses the 1x pin bitmaps, so at 1 the cache keys and sizes have
 * to be the ones they always were, and a bigger pin must not take their cache entry.
 */
public class IconScaleTest {
	@Test
	public void oneKeepsTheKey() {
		assertEquals("slimRoute|false|color", IconScale.key("slimRoute|false|color", 1));
	}

	@Test
	public void otherScalesGetTheirOwnKey() {
		assertNotEquals("slimRoute|false|color", IconScale.key("slimRoute|false|color", 2));
		assertNotEquals(IconScale.key("style|30|[]", 2), IconScale.key("style|30|[]", 3));
	}

	@Test
	public void oneKeepsTheSize() {
		assertEquals(73, IconScale.px(73, 1));
	}

	@Test
	public void doubleIsExactlyTwiceTheSize() {
		// The member cards lay the pin out at twice the 1x size, which the bitmap has to fill.
		for (int px = 1; px < 1000; px++) {
			assertEquals(2 * px, IconScale.px(px, 2));
		}
	}

	@Test
	public void neverUnderOnePixel() {
		assertEquals(1, IconScale.px(1, 0.1f));
	}
}
