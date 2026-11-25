package com.climbtheworld.app.map.style;

import java.util.Objects;

/**
 * A named MapLibre style endpoint available to the application.
 */
public final class MapStyleDefinition {
	private final String id;
	private final String displayName;
	private final String styleUrl;
	// The layer of the style the relief is shaded under, see TerrainShading; null for none.
	private final String shadingBelowLayerId;

	public MapStyleDefinition(String id, String displayName, String styleUrl) {
		this(id, displayName, styleUrl, null);
	}

	/**
	 * @param shadingBelowLayerId the layer of the style to shade the relief under, see
	 *                            {@link TerrainShading}; null to leave the style as it is
	 */
	public MapStyleDefinition(String id, String displayName, String styleUrl,
			String shadingBelowLayerId) {
		this.id = requireNonEmpty(id, "id");
		this.displayName = requireNonEmpty(displayName, "displayName");
		this.styleUrl = requireNonEmpty(styleUrl, "styleUrl");
		this.shadingBelowLayerId = shadingBelowLayerId == null ? null
				: requireNonEmpty(shadingBelowLayerId, "shadingBelowLayerId");
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

	/**
	 * @return whether the relief is shaded over the style, see {@link TerrainShading}
	 */
	public boolean hasTerrainShading() {
		return shadingBelowLayerId != null;
	}

	/**
	 * @return the layer of the style the relief is shaded under, null when it is not shaded
	 */
	public String getShadingBelowLayerId() {
		return shadingBelowLayerId;
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
				&& styleUrl.equals(definition.styleUrl)
				&& Objects.equals(shadingBelowLayerId, definition.shadingBelowLayerId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, displayName, styleUrl, shadingBelowLayerId);
	}
}
