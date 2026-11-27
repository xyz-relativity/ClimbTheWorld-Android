package com.climbtheworld.app.map.model;

import java.util.Objects;

/**
 * The persistent position of a map camera.
 */
public final class MapCameraState {
	private final MapCoordinate center;
	private final double zoom;
	private final double bearing;
	private final double tilt;

	public MapCameraState(MapCoordinate center, double zoom) {
		this(center, zoom, 0);
	}

	public MapCameraState(MapCoordinate center, double zoom, double bearing) {
		this(center, zoom, bearing, 0);
	}

	/**
	 * The zoom may be negative, as MapLibre's globe projection zooms below 0 away from the
	 * equator; it only has to be a number.
	 *
	 * @param tilt in degrees from looking straight down, 0 for a flat map
	 */
	public MapCameraState(MapCoordinate center, double zoom, double bearing, double tilt) {
		if (Double.isNaN(zoom) || Double.isInfinite(zoom)) {
			throw new IllegalArgumentException("Zoom must be a finite number");
		}

		this.center = Objects.requireNonNull(center, "center");
		this.zoom = zoom;
		this.bearing = bearing;
		this.tilt = tilt;
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

	public double getTilt() {
		return tilt;
	}
}
