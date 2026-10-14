package com.climbtheworld.app.map.style;

import com.climbtheworld.app.BuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The application's supported basemap styles.
 */
public final class MapStyleRegistry {
	public static final MapStyleDefinition OPEN_FREE_MAP_LIBERTY = new MapStyleDefinition(
			"openfreemap-liberty",
			"OpenFreeMap Liberty",
			"https://tiles.openfreemap.org/styles/liberty");
	// Raster tiles wrapped in a bundled style, which also points at OpenFreeMap's glyphs for the
	// cluster labels.
	public static final MapStyleDefinition OPEN_TOPO_MAP = new MapStyleDefinition(
			"opentopomap",
			"OpenTopoMap",
			"asset://maplibre/opentopomap.json");
	static final String MAPBOX_SATELLITE_ID = "mapbox-satellite";
	// Built at runtime around the token, which only the build knows. Tiles are fetched up to zoom
	// 19, about the imagery's own resolution, and scaled up past it, which saves tile requests. The
	// credits are the four Mapbox requires for its satellite imagery.
	private static final String MAPBOX_SATELLITE_STYLE = """
			{
			  "version": 8,
			  "name": "Mapbox Satellite",
			  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
			  "sources": {
			    "mapbox-satellite": {
			      "type": "raster",
			      "tiles": [
			        "https://api.mapbox.com/v4/mapbox.satellite/{z}/{x}/{y}@2x.jpg90?access_token=ACCESS_TOKEN"
			      ],
			      "tileSize": 256,
			      "maxzoom": 19,
			      "attribution": "<a href=\\"https://www.mapbox.com/about/maps/\\">&copy; Mapbox</a> <a href=\\"https://www.openstreetmap.org/copyright\\">&copy; OpenStreetMap</a> <a href=\\"https://apps.mapbox.com/feedback/\\">Improve this map</a> <a href=\\"https://www.maxar.com/\\">&copy; Maxar</a>"
			    }
			  },
			  "layers": [
			    {
			      "id": "mapbox-satellite",
			      "type": "raster",
			      "source": "mapbox-satellite"
			    }
			  ]
			}
			""";

	private static final List<MapStyleDefinition> AVAILABLE_STYLES =
			availableStyles(BuildConfig.MAPBOX_ACCESS_TOKEN);

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

	/**
	 * The styles a build offers: the satellite imagery needs a Mapbox token, so builds without one
	 * leave it out.
	 */
	static List<MapStyleDefinition> availableStyles(String mapboxAccessToken) {
		List<MapStyleDefinition> styles =
				new ArrayList<>(Arrays.asList(OPEN_FREE_MAP_LIBERTY, OPEN_TOPO_MAP));
		if (!mapboxAccessToken.isEmpty()) {
			styles.add(MapStyleDefinition.fromJson(MAPBOX_SATELLITE_ID, "Mapbox Satellite",
					MAPBOX_SATELLITE_STYLE.replace("ACCESS_TOKEN", mapboxAccessToken), true));
		}
		return Collections.unmodifiableList(styles);
	}
}
