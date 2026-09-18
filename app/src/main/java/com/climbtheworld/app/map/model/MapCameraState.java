package com.climbtheworld.app.map.model;

import java.util.Objects;

/**
 * The persistent position of a map camera.
 */
public final class MapCameraState {
	private final MapCoordinate center;
	private final double zoom;

	public MapCameraState(MapCoordinate center, double zoom) {
		if (zoom < 0) {
			throw new IllegalArgumentException("Zoom must not be negative");
		}

		this.center = Objects.requireNonNull(center, "center");
		this.zoom = zoom;
	}

	public MapCoordinate getCenter() {
		return center;
	}

	public double getZoom() {
		return zoom;
	}
}
