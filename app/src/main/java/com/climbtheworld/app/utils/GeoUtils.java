package com.climbtheworld.app.utils;

import com.climbtheworld.app.storage.database.GeoNode;

public class GeoUtils {
	public static final double EARTH_RADIUS_M = 6378137;
	public static final double EARTH_RADIUS_KM = EARTH_RADIUS_M / 1000;

	private GeoUtils() {
		//hide constructor.
	}

	/**
	 * Computes the azimuth between 2 points: the compass bearing (0 is north, growing clockwise)
	 * the observer has to face to look at the destination.
	 *
	 * @param obs Observer point
	 * @param poi Destination point
	 * @return Returns the azimuth in degree, between -180 and 180
	 */
	public static double calculateTheoreticalAzimuth(GeoNode obs, GeoNode poi) {
		// Initial bearing of the great circle. A degree of longitude shrinks with the cosine of
		// the latitude, so the raw coordinate differences are not east and north distances.
		double obsLatitude = Math.toRadians(obs.decimalLatitude);
		double poiLatitude = Math.toRadians(poi.decimalLatitude);
		double deltaLongitude = Math.toRadians(poi.decimalLongitude - obs.decimalLongitude);

		return Math.toDegrees(Math.atan2(
				Math.sin(deltaLongitude) * Math.cos(poiLatitude),
				Math.cos(obsLatitude) * Math.sin(poiLatitude)
						- Math.sin(obsLatitude) * Math.cos(poiLatitude) * Math.cos(deltaLongitude)));
	}

	/**
	 * Computes the angle above the horizontal at which the observer sees the destination.
	 *
	 * @param observerElevation Observer elevation, NaN when unknown
	 * @param poiElevation      Destination elevation, NaN when unknown
	 * @param distanceMeters    Distance between the points
	 * @return Returns the angle in degree, or 0 when either elevation is unknown
	 */
	public static double calculateElevationAngle(double observerElevation, double poiElevation,
	                                             double distanceMeters) {
		if (Double.isNaN(observerElevation) || Double.isNaN(poiElevation)) {
			return 0;
		}
		return Math.toDegrees(Math.atan2(poiElevation - observerElevation, distanceMeters));
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

	public static Vector2d latLongOffset(Double decimalOriginLat, Vector2d offsetM) {
		Vector2d result = new Vector2d();

		//Coordinate offsets in radians
		result.y = Math.toDegrees(offsetM.y/EARTH_RADIUS_M);
		result.x = Math.toDegrees(offsetM.x/(EARTH_RADIUS_M * Math.cos(Math.PI * decimalOriginLat/180)));

		return result;
	}
}
