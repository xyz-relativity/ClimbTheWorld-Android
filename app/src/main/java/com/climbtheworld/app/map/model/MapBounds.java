package com.climbtheworld.app.map.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

	/**
	 * The smallest bounds holding the points. They cross the antimeridian when that is narrower,
	 * as for a crag with routes on both sides of it, but not for one on both sides of Greenwich.
	 */
	public static MapBounds around(Collection<MapCoordinate> points) {
		if (points.isEmpty()) {
			throw new IllegalArgumentException("Bounds need at least one point");
		}
		double north = -90;
		double south = 90;
		double west = 180;
		double east = -180;
		// The sides of the bounds across the antimeridian: the westmost point east of Greenwich
		// and the eastmost one west of it.
		double crossingWest = 180;
		double crossingEast = -180;
		for (MapCoordinate point : points) {
			north = Math.max(north, point.getLatitude());
			south = Math.min(south, point.getLatitude());
			west = Math.min(west, point.getLongitude());
			east = Math.max(east, point.getLongitude());
			if (point.getLongitude() >= 0) {
				crossingWest = Math.min(crossingWest, point.getLongitude());
			} else {
				crossingEast = Math.max(crossingEast, point.getLongitude());
			}
		}
		if (west < 0 && east >= 0 && crossingEast + 360 - crossingWest < east - west) {
			return new MapBounds(north, crossingEast, south, crossingWest);
		}
		return new MapBounds(north, east, south, west);
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

	/** The degrees of longitude the bounds span, counted across the antimeridian. */
	public double getLongitudeSpan() {
		return crossesAntimeridian() ? east - west + 360 : east - west;
	}

	/** The middle of the bounds, counted across the antimeridian. */
	public MapCoordinate getCenter() {
		double longitude = west + getLongitudeSpan() / 2;
		if (longitude > 180) {
			longitude -= 360;
		}
		return new MapCoordinate((north + south) / 2, longitude);
	}

	/**
	 * The bounds grown on every side by the fraction of their size, on past the antimeridian and
	 * up to the poles.
	 */
	public MapBounds padded(double fraction) {
		double latitudePadding = (north - south) * fraction;
		double longitudeSpan = getLongitudeSpan();
		double longitudePadding = longitudeSpan * fraction;
		return fromUnwrapped(north + latitudePadding, west + longitudeSpan + longitudePadding,
				south - latitudePadding, west - longitudePadding);
	}

	/**
	 * The bounds as boxes that do not cross the antimeridian, for comparing longitudes as numbers,
	 * like a database query does: the bounds themselves, or their parts on either side of it.
	 */
	public List<MapBounds> splitAtAntimeridian() {
		if (!crossesAntimeridian()) {
			return Collections.singletonList(this);
		}
		return Arrays.asList(new MapBounds(north, 180, south, west),
				new MapBounds(north, east, south, -180));
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
