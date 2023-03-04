package com.climbtheworld.app.utils;

import com.climbtheworld.app.storage.database.GeoNode;

public class GeoUtils {
	public static final double EARTH_RADIUS_M = 6378137;
	public static final double EARTH_RADIUS_KM = EARTH_RADIUS_M / 1000;

	private GeoUtils() {
		//hide constructor.
	}

	/**
	 * Computes the azimuth between 2 points
	 *
	 * @param obs Observer point
	 * @param poi Destination point
	 * @return Returns the azimuth in degree
	 */
	public static double calculateTheoreticalAzimuth(GeoNode obs, GeoNode poi) {
		return Math.toDegrees(Math.atan2(poi.decimalLongitude - obs.decimalLongitude,
				poi.decimalLatitude - obs.decimalLatitude));
	}

	/**
	 * Calculate distance between 2 coordinates using the haversine algorithm.
	 *
	 * @param obs Observer location
	 * @param poi Point of interest location
	 * @return Shortest as the crow flies distance in meters.
	 */
	public static double calculateDistance(GeoNode obs, GeoNode poi) {
		double dLat = Math.toRadians(poi.decimalLatitude - obs.decimalLatitude);
		double dLon = Math.toRadians(poi.decimalLongitude - obs.decimalLongitude);

		double lat1 = Math.toRadians(obs.decimalLatitude);
		double lat2 = Math.toRadians(poi.decimalLatitude);

		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
				Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_M * c;
	}

	/**
	 * Calculates shortest difference between 2 angels
	 *
	 * @param a origin angle
	 * @param b dest angle
	 * @return angle difference
	 */
	public static double diffAngle(double a, double b) {
//        double x = Math.toRadians(a);
//        double y = Math.toRadians(b);
//        return Math.toDegrees(Math.atan2(Math.sin(x-y), Math.cos(x-y)));

		//this way should be more efficient
		double d = Math.abs(a - b) % 360;
		double r = d > 180 ? 360 - d : d;

		int sign = (a - b >= 0 && a - b <= 180) || (a - b <= -180 && a - b >= -360) ? 1 : -1;
		return (r * sign);
	}
}
