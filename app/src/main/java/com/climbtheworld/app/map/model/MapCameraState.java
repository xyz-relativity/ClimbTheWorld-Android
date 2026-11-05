package com.climbtheworld.app.map.model;

import java.util.Objects;

/**
 * The persistent position of a map camera.
 */
public final class MapCameraState {
	private final MapCoordinate center;
	private final double zoom;
	private final double bearing;

	public MapCameraState(MapCoordinate center, double zoom) {
		this(center, zoom, 0);
	}

	/**
	 * The zoom may be negative, as MapLibre's globe projection zooms below 0 away from the
	 * equator; it only has to be a number.
	 */
	public MapCameraState(MapCoordinate center, double zoom, double bearing) {
		if (Double.isNaN(zoom) || Double.isInfinite(zoom)) {
			throw new IllegalArgumentException("Zoom must be a finite number");
		}

		this.center = Objects.requireNonNull(center, "center");
		this.zoom = zoom;
		this.bearing = bearing;
	}

	public MapCoordinate getCenter() {
		return center;
	}

	public double getZoom() {
		return zoom;
	}

	public double getBearing() {
		return bearing;
	}
}
