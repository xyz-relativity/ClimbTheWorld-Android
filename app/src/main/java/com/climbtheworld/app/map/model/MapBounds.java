package com.climbtheworld.app.map.model;

import java.util.Objects;

/**
 * A WGS84 bounding box that may cross the antimeridian.
 */
public final class MapBounds {
	private final double north;
	private final double east;
	private final double south;
	private final double west;

	public MapBounds(double north, double east, double south, double west) {
		if (north < -90 || north > 90 || south < -90 || south > 90) {
			throw new IllegalArgumentException("Latitude bounds must be between -90 and 90 degrees");
		}
		if (east < -180 || east > 180 || west < -180 || west > 180) {
			throw new IllegalArgumentException("Longitude bounds must be between -180 and 180 degrees");
		}
		if (north < south) {
			throw new IllegalArgumentException("North bound must not be south of the south bound");
		}

		this.north = north;
		this.east = east;
		this.south = south;
		this.west = west;
	}

	/**
	 * Bounds whose longitudes run on past ±180 instead of wrapping, with east never west of west.
	 * MapLibre gives a view across the antimeridian, or one showing the world more than once,
	 * that way, and so does a box around a point near the antimeridian. Wider than the world, they
	 * cover every longitude. Latitudes past a pole stop at it.
	 */
	public static MapBounds fromUnwrapped(double north, double east, double south, double west) {
		double clampedNorth = Math.min(90, north);
		double clampedSouth = Math.max(-90, south);
		if (east - west >= 360) {
			return new MapBounds(clampedNorth, 180, clampedSouth, -180);
		}
		// Whole turns move the box until its west side is in [-180, 180).
		double shift = 360 * Math.floor((west + 180) / 360);
		double wrappedEast = east - shift;
		if (wrappedEast > 180) {
			wrappedEast -= 360;
		}
		return new MapBounds(clampedNorth, wrappedEast, clampedSouth, west - shift);
	}

	public double getNorth() {
		return north;
	}

	public double getEast() {
		return east;
	}

	public double getSouth() {
		return south;
	}

	public double getWest() {
		return west;
	}

	public boolean crossesAntimeridian() {
		return west > east;
	}

	public boolean contains(MapCoordinate coordinate) {
		if (coordinate.getLatitude() > north || coordinate.getLatitude() < south) {
			return false;
		}
		if (!crossesAntimeridian()) {
			return coordinate.getLongitude() >= west && coordinate.getLongitude() <= east;
		}
		return coordinate.getLongitude() >= west || coordinate.getLongitude() <= east;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MapBounds)) {
			return false;
		}

		MapBounds bounds = (MapBounds) other;
		return Double.compare(north, bounds.north) == 0
				&& Double.compare(east, bounds.east) == 0
				&& Double.compare(south, bounds.south) == 0
				&& Double.compare(west, bounds.west) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(north, east, south, west);
	}
}
