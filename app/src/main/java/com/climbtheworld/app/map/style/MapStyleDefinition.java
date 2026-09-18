package com.climbtheworld.app.map.style;

import java.util.Objects;

/**
 * A named MapLibre style endpoint available to the application.
 */
public final class MapStyleDefinition {
	private final String id;
	private final String displayName;
	private final String styleUrl;

	public MapStyleDefinition(String id, String displayName, String styleUrl) {
		this.id = requireNonEmpty(id, "id");
		this.displayName = requireNonEmpty(displayName, "displayName");
		this.styleUrl = requireNonEmpty(styleUrl, "styleUrl");
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

	public String getStyleUrl() {
		return styleUrl;
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
				&& styleUrl.equals(definition.styleUrl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, displayName, styleUrl);
	}
}
