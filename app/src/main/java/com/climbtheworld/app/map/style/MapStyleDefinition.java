package com.climbtheworld.app.map.style;

import java.util.Objects;

/**
 * A named MapLibre style available to the application, given by its URL or, for a style built at
 * runtime, by its JSON.
 */
public final class MapStyleDefinition {
	private final String id;
	private final String displayName;
	private final String styleUrl;
	private final String styleJson;
	private final boolean mapboxLogoRequired;

	public MapStyleDefinition(String id, String displayName, String styleUrl) {
		this(id, displayName, requireNonEmpty(styleUrl, "styleUrl"), null, false);
	}

	private MapStyleDefinition(String id, String displayName, String styleUrl, String styleJson,
	                           boolean mapboxLogoRequired) {
		this.id = requireNonEmpty(id, "id");
		this.displayName = requireNonEmpty(displayName, "displayName");
		this.styleUrl = styleUrl;
		this.styleJson = styleJson;
		this.mapboxLogoRequired = mapboxLogoRequired;
	}

	/**
	 * @param mapboxLogoRequired whether the style shows Mapbox tiles, which Mapbox only allows with
	 *                           its logo on the map
	 */
	public static MapStyleDefinition fromJson(String id, String displayName, String styleJson,
	                                          boolean mapboxLogoRequired) {
		return new MapStyleDefinition(id, displayName, null, requireNonEmpty(styleJson, "styleJson"),
				mapboxLogoRequired);
	}

	private static String requireNonEmpty(String value, String name) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(name + " must not be empty");
		}
		return value;
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * The style's URL, or null for a style given by its JSON.
	 */
	public String getStyleUrl() {
		return styleUrl;
	}

	/**
	 * The style's JSON, or null for a style given by its URL.
	 */
	public String getStyleJson() {
		return styleJson;
	}

	public boolean isMapboxLogoRequired() {
		return mapboxLogoRequired;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MapStyleDefinition)) {
			return false;
		}

		MapStyleDefinition definition = (MapStyleDefinition) other;
		return id.equals(definition.id)
				&& displayName.equals(definition.displayName)
				&& Objects.equals(styleUrl, definition.styleUrl)
				&& Objects.equals(styleJson, definition.styleJson)
				&& mapboxLogoRequired == definition.mapboxLogoRequired;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, displayName, styleUrl, styleJson, mapboxLogoRequired);
	}
}
