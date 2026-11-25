package com.climbtheworld.app.map.style;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The application's supported basemap styles.
 */
public final class MapStyleRegistry {
	// With the relief shaded in, see TerrainShading: under its rivers, lakes, roads and labels,
	// over its land cover.
	public static final MapStyleDefinition OPEN_FREE_MAP_LIBERTY = new MapStyleDefinition(
			"openfreemap-liberty",
			"OpenFreeMap Liberty",
			"https://tiles.openfreemap.org/styles/liberty",
			"waterway_tunnel");
	// Raster tiles wrapped in a bundled style, which also points at OpenFreeMap's glyphs for the
	// cluster labels.
	public static final MapStyleDefinition OPEN_TOPO_MAP = new MapStyleDefinition(
			"opentopomap",
			"OpenTopoMap",
			"asset://maplibre/opentopomap.json");

	private static final List<MapStyleDefinition> AVAILABLE_STYLES = Collections.unmodifiableList(
			Arrays.asList(OPEN_FREE_MAP_LIBERTY, OPEN_TOPO_MAP));

	private MapStyleRegistry() {
	}

	public static MapStyleDefinition getDefaultStyle() {
		return OPEN_FREE_MAP_LIBERTY;
	}

	public static MapStyleDefinition getStyle(String id) {
		for (MapStyleDefinition style : AVAILABLE_STYLES) {
			if (style.getId().equals(id)) {
				return style;
			}
		}
		return getDefaultStyle();
	}

	public static List<MapStyleDefinition> getAvailableStyles() {
		return AVAILABLE_STYLES;
	}
}
