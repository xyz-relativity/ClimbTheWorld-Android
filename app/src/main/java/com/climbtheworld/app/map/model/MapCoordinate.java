package com.climbtheworld.app.map.model;

import java.util.Objects;

/**
 * A geographic point expressed in WGS84 degrees.
 */
public final class MapCoordinate {
	private final double latitude;
	private final double longitude;
	private final double altitudeMeters;

	public MapCoordinate(double latitude, double longitude) {
		this(latitude, longitude, 0);
	}

	public MapCoordinate(double latitude, double longitude, double altitudeMeters) {
		if (latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees");
		}
		if (longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees");
		}

		this.latitude = latitude;
		this.longitude = longitude;
		this.altitudeMeters = altitudeMeters;
	}

	public double getLatitude() {
		return latitude;
	}

	public static MapCoordinate fromDelimitedString(String value) {
		String[] coordinates = value.split(",");
		if (coordinates.length < 2) {
			throw new IllegalArgumentException("Coordinate must contain latitude and longitude");
		}
		double altitude = coordinates.length > 2 ? Double.parseDouble(coordinates[2]) : 0;
		return new MapCoordinate(
				Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]), altitude);
	}

	public String toDelimitedString() {
		return latitude + "," + longitude + "," + altitudeMeters;
	}

	public double getLongitude() {
		return longitude;
	}

	public double getAltitudeMeters() {
		return altitudeMeters;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof MapCoordinate)) {
			return false;
		}

		MapCoordinate coordinate = (MapCoordinate) other;
		return Double.compare(latitude, coordinate.latitude) == 0
				&& Double.compare(longitude, coordinate.longitude) == 0
				&& Double.compare(altitudeMeters, coordinate.altitudeMeters) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(latitude, longitude, altitudeMeters);
	}
}
