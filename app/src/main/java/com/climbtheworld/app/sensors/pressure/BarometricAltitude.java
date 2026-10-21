package com.climbtheworld.app.sensors.pressure;

/**
 * The altitude from the barometer, anchored to GPS. The altitude the air pressure tells is off by
 * tens of meters, as the weather moves the pressure, but its changes are true to about a meter,
 * where GPS altitudes jump by several meters from one fix to the next. So the offset between the
 * GPS altitudes and the barometer's, averaged over the latest fixes, anchors the barometer: as
 * accurate as GPS on average, but steady, and following climbs and descents as they happen.
 */
public class BarometricAltitude {
	// How much of each new reading the smoothed pressure altitude takes, as readings are a meter
	// or two apart from one to the next.
	static final double READING_SMOOTHING = 0.1;
	// How much of each GPS fix the anchor takes: about the latest 20 fixes count.
	static final double ANCHOR_SMOOTHING = 0.05;

	// NaN until there is a reading.
	private double pressureAltitude = Double.NaN;
	// From the barometer's altitude to the GPS one, NaN until there are both.
	private double offset = Double.NaN;

	/**
	 * @param meters the altitude a pressure reading tells, in the standard atmosphere
	 */
	public void addReading(double meters) {
		pressureAltitude = Double.isNaN(pressureAltitude) ? meters
				: pressureAltitude + (meters - pressureAltitude) * READING_SMOOTHING;
	}

	/**
	 * Anchors the barometer to a GPS altitude, and tells the altitude to go by.
	 *
	 * @param gpsMeters the GPS altitude above sea level, 0 when unknown, see SeaLevelAltitude
	 * @return the anchored barometer's altitude once there are a reading and a GPS altitude to
	 * anchor it, and then even without a GPS altitude; else the GPS altitude
	 */
	public double anchor(double gpsMeters) {
		if (Double.isNaN(pressureAltitude)) {
			return gpsMeters;
		}
		if (gpsMeters != 0) {
			double fixOffset = gpsMeters - pressureAltitude;
			offset = Double.isNaN(offset) ? fixOffset
					: offset + (fixOffset - offset) * ANCHOR_SMOOTHING;
		}
		return Double.isNaN(offset) ? gpsMeters : pressureAltitude + offset;
	}
}
