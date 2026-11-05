package com.climbtheworld.app.map.model;

import java.util.Collection;

/**
 * Shared zoom thresholds for the map's semantic detail levels.
 */
public final class MapZoomLevels {
	public static final float AREA_MIN = 7f;
	public static final float CRAG_MIN = 15f;
	public static final float POI_AND_ROUTE_MIN = 18f;

	private MapZoomLevels() {
	}

	/**
	 * Whether one of the levels lies between the two zooms, either one included: what shows from
	 * a level on differs from what shows below it, so a map at the level shows something else
	 * than one just below. The same zoom crosses nothing.
	 */
	public static boolean crosses(double fromZoom, double toZoom, Collection<Float> levels) {
		if (fromZoom == toZoom) {
			return false;
		}
		double low = Math.min(fromZoom, toZoom);
		double high = Math.max(fromZoom, toZoom);
		for (float level : levels) {
			if (level >= low && level <= high) {
				return true;
			}
		}
		return false;
	}
}
