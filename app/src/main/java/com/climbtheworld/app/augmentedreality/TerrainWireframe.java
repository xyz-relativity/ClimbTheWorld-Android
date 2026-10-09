package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.utils.GeoUtils;

/**
 * Terrain around the observer, sampled on a polar grid: rays at regular azimuths, crossed by rings
 * that get further apart with the distance, as farther terrain covers less of the view. It is
 * projected into the AR view like the POIs, leaving out the terrain hidden behind closer terrain,
 * along with the skyline: the highest terrain each ray sees.
 * <p>
 * Elevations are sampled around a centre and kept while the observer stays close to it, so
 * following the camera only takes the projection.
 */
public class TerrainWireframe {
	public static final int RAY_COUNT = 144;
	// Close to the feet, so the terrain reaches the bottom of the view however far down it looks.
	static final double FIRST_RING_METERS = 0.5;
	private static final double RING_GROWTH = 1.1;
	private static final double RADIUS_METERS = 3000;
	static final double[] RING_DISTANCES = ringDistances();
	public static final int RING_COUNT = RING_DISTANCES.length;
	// The rings are sampled closely so the rays and the skyline follow the terrain, but drawing
	// them all would bury the view in lines.
	public static final int RING_LINE_STEP = 3;
	// How far the observer can move before the terrain is sampled around them again, so the rays
	// stay about centred on them. The closest samples can then be behind them, see project.
	private static final double RECENTRE_DISTANCE_METERS = 1;
	// How often the terrain is sampled again while some of it is still loading.
	private static final long RETRY_DELAY_MS = 1000;
	// The share of the Earth curvature that light bending through the atmosphere makes up for.
	private static final double REFRACTION = 0.13;
	// Terrain only just below what hides it, like flat ground, is kept, so it does not flicker.
	private static final double OCCLUSION_SLACK_METERS = 0.5;
	// Points too far from the camera axis are left out, as their projection runs off to infinity.
	private static final double MIN_FORWARD = 0.1;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	private static final double[] RAY_SIN = new double[RAY_COUNT];
	private static final double[] RAY_COS = new double[RAY_COUNT];
	private static final double[] CURVATURE_DROP = new double[RING_COUNT];

	static {
		for (int ray = 0; ray < RAY_COUNT; ray++) {
			double azimuth = 2 * Math.PI * ray / RAY_COUNT;
			RAY_SIN[ray] = Math.sin(azimuth);
			RAY_COS[ray] = Math.cos(azimuth);
		}
		for (int ring = 0; ring < RING_COUNT; ring++) {
			CURVATURE_DROP[ring] = (1 - REFRACTION) * RING_DISTANCES[ring] * RING_DISTANCES[ring]
					/ (2 * GeoUtils.EARTH_RADIUS_M);
		}
	}

	private final ElevationSource source;
	// Per sample, indexed by ray * RING_COUNT + ring. NaN while unknown.
	private final float[] elevations = new float[RAY_COUNT * RING_COUNT];
	private final float[] screenX = new float[RAY_COUNT * RING_COUNT];
	private final float[] screenY = new float[RAY_COUNT * RING_COUNT];
	private final boolean[] drawable = new boolean[RAY_COUNT * RING_COUNT];
	// Per ray, the sample on the skyline, or -1.
	private final int[] skyline = new int[RAY_COUNT];
	private double centreLatitude = Double.NaN;
	private double centreLongitude = Double.NaN;
	private boolean complete;
	private long sampledAtMillis;

	public TerrainWireframe(ElevationSource source) {
		this.source = source;
	}

	private static double[] ringDistances() {
		int count = (int) Math.floor(
				Math.log(RADIUS_METERS / FIRST_RING_METERS) / Math.log(RING_GROWTH)) + 1;
		double[] distances = new double[count];
		for (int ring = 0; ring < count; ring++) {
			distances[ring] = FIRST_RING_METERS * Math.pow(RING_GROWTH, ring);
		}
		return distances;
	}

	public static int sampleIndex(int ray, int ring) {
		return ray * RING_COUNT + ring;
	}

	/**
	 * Samples the terrain again when the observer moved away from the centre of the samples, or
	 * while some of it is still loading.
	 *
	 * @return whether the terrain was sampled again
	 */
	public boolean update(double latitude, double longitude, long nowMillis) {
		boolean moved = Double.isNaN(centreLatitude)
				|| Math.hypot(eastOfCentre(longitude), northOfCentre(latitude))
				> RECENTRE_DISTANCE_METERS;
		if (!moved && (complete || nowMillis - sampledAtMillis < RETRY_DELAY_MS)) {
			return false;
		}

		if (moved) {
			centreLatitude = latitude;
			centreLongitude = longitude;
		}
		sample();
		sampledAtMillis = nowMillis;
		return true;
	}

	private void sample() {
		double metersPerDegreeLongitude =
				METERS_PER_DEGREE * Math.cos(Math.toRadians(centreLatitude));
		complete = true;
		for (int ray = 0; ray < RAY_COUNT; ray++) {
			for (int ring = 0; ring < RING_COUNT; ring++) {
				double distance = RING_DISTANCES[ring];
				float elevation = (float) source.getElevation(
						centreLatitude + distance * RAY_COS[ray] / METERS_PER_DEGREE,
						centreLongitude + distance * RAY_SIN[ray] / metersPerDegreeLongitude,
						distance);
				elevations[sampleIndex(ray, ring)] = elevation;
				complete &= !Float.isNaN(elevation);
			}
		}
	}

	private double northOfCentre(double latitude) {
		return (latitude - centreLatitude) * METERS_PER_DEGREE;
	}

	private double eastOfCentre(double longitude) {
		return (longitude - centreLongitude) * METERS_PER_DEGREE
				* Math.cos(Math.toRadians(centreLatitude));
	}

	/**
	 * Projects the samples into the AR view the way {@link AugmentedRealityUtils#getXYPosition}
	 * projects the POIs, leaving the roll to the caller. The results are read with
	 * {@link #isDrawable}, {@link #getScreenX}, {@link #getScreenY} and {@link #getSkyline}.
	 *
	 * @param eyeElevation elevation of the observer's eye above sea level, in meters
	 * @param azimuthDeg   compass azimuth of the camera axis
	 * @param pitchDeg     angle of the camera axis above the horizontal
	 * @param focalX       horizontal distance in pixel between the projection centre and the view
	 * @param focalY       vertical distance in pixel between the projection centre and the view
	 * @param centreX      horizontal position of the view centre
	 * @param centreY      vertical position of the view centre
	 */
	public void project(double latitude, double longitude, double eyeElevation,
	                    double azimuthDeg, double pitchDeg, double focalX, double focalY,
	                    double centreX, double centreY) {
		double observerEast = eastOfCentre(longitude);
		double observerNorth = northOfCentre(latitude);
		double sinAzimuth = Math.sin(Math.toRadians(azimuthDeg));
		double cosAzimuth = Math.cos(Math.toRadians(azimuthDeg));
		double sinPitch = Math.sin(Math.toRadians(pitchDeg));
		double cosPitch = Math.cos(Math.toRadians(pitchDeg));

		for (int ray = 0; ray < RAY_COUNT; ray++) {
			skyline[ray] = -1;
			double highestSlope = Double.NEGATIVE_INFINITY;
			for (int ring = 0; ring < RING_COUNT; ring++) {
				int index = sampleIndex(ray, ring);
				drawable[index] = false;
				if (Float.isNaN(elevations[index])) {
					continue;
				}

				// Relative to the eye, in meters.
				double east = RING_DISTANCES[ring] * RAY_SIN[ray] - observerEast;
				double north = RING_DISTANCES[ring] * RAY_COS[ray] - observerNorth;
				double up = elevations[index] - CURVATURE_DROP[ring] - eyeElevation;
				double horizontal = Math.sqrt(east * east + north * north);

				// Walking out along the ray, terrain is hidden by any closer terrain seen higher.
				// The observer strays up to RECENTRE_DISTANCE_METERS from the centre, so the
				// closest samples of a ray can be behind them, out of the way of the rest.
				if (east * RAY_SIN[ray] + north * RAY_COS[ray] > 0) {
					double slope = up / horizontal;
					boolean hidden = slope < highestSlope - OCCLUSION_SLACK_METERS / horizontal;
					if (slope > highestSlope) {
						highestSlope = slope;
						skyline[ray] = index;
					}
					if (hidden) {
						continue;
					}
				}

				double right = east * cosAzimuth - north * sinAzimuth;
				double along = north * cosAzimuth + east * sinAzimuth;
				double forward = along * cosPitch + up * sinPitch;
				if (forward < MIN_FORWARD * Math.sqrt(horizontal * horizontal + up * up)) {
					continue;
				}
				double down = along * sinPitch - up * cosPitch;
				screenX[index] = (float) (centreX + focalX * right / forward);
				screenY[index] = (float) (centreY + focalY * down / forward);
				drawable[index] = true;
			}
		}
	}

	/**
	 * @return whether the sample is known, not hidden and in front of the camera, as of the last
	 * projection
	 */
	public boolean isDrawable(int index) {
		return drawable[index];
	}

	public float getScreenX(int index) {
		return screenX[index];
	}

	public float getScreenY(int index) {
		return screenY[index];
	}

	/**
	 * @return the sample on the skyline along the ray, or -1 when the ray has no known sample
	 */
	public int getSkyline(int ray) {
		return skyline[ray];
	}

	public interface ElevationSource {
		/**
		 * @param distanceMeters distance from the observer, for the source to pick its detail
		 * @return the ground elevation above sea level in meters, or NaN while it is not known
		 */
		double getElevation(double latitude, double longitude, double distanceMeters);
	}
}
