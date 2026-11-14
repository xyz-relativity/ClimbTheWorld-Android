package com.climbtheworld.app.map.marker;

/**
 * The size of a cached pin bitmap drawn some times bigger than on the map, for a view that shows
 * the pin that big, see PoiMarkerDrawable.getDrawable(float). At 1 the key and the size are the
 * map's, so the bitmaps every other view shares stay the same entries.
 */
final class IconScale {
	private IconScale() {
	}

	/** The cache key of the bitmap at the given scale, the base key itself at 1. */
	static String key(String baseKey, float scale) {
		return scale == 1 ? baseKey : baseKey + "|x" + scale;
	}

	/** A size in pixels at the given scale, never under one pixel. */
	static int px(int px, float scale) {
		return scale == 1 ? px : Math.max(Math.round(px * scale), 1);
	}
}
