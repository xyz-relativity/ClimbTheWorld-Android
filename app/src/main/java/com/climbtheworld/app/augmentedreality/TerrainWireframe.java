package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.utils.GeoUtils;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Terrain around the observer, sampled on a polar grid: rays at regular azimuths, crossed by rings
 * that get further apart with the distance, as farther terrain covers less of the view. It is
 * projected into the AR view like the POIs, leaving out the terrain hidden behind closer terrain,
 * along with the skyline: the highest terrain each ray sees.
 * <p>
 * Elevations are sampled around a centre and kept while the observer stays close to it, so
 * following the camera only takes the projection.
 * <p>
 * A sampling reads thousands of elevations, which is too much for the frame it would hold up, so
 * it runs off the main thread, see {@link Sampler}: one at a time, while the samples before it
 * stay on display. All the rest is only to be used from the main thread.
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
	// stay about centred on them. In between, the closest rings follow them, see VIEWPOINT_SHARE.
	private static final double RECENTRE_DISTANCE_METERS = 1;
	// Between samplings, each ring is seen from its own viewpoint, on the way from the centre of
	// the samples to the observer. The close rings are seen from about the centre, so they stay
	// around the feet: seen from the observer, the point the rays meet would wander up to
	// RECENTRE_DISTANCE_METERS away, in plain view when looking down, and jump back under them
	// with every sampling. The far rings are seen from about the observer, so they stay on the
	// terrain they show. Half way at this distance.
	private static final double VIEWPOINT_HALFWAY_METERS = 5;
	// Up to this far from the centre of the samples, each ring's viewpoint stays closer to the
	// centre than the ring is, so every sample is ahead of it along its ray, see project. Past
	// it, as after the observer moved on while the view was hidden or paused, the samples
	// cannot be drawn for them any more, and go, see update.
	static final double MAX_VIEWPOINT_DISTANCE_METERS = 2 * VIEWPOINT_HALFWAY_METERS;
	// The terrain is sampled again once more of it loaded, this long after the last sampling at
	// the soonest, as its tiles come in one after the other.
	static final long TERRAIN_RESAMPLE_DELAY_MS = 1000;
	// While some of it is missing, it is also sampled again this often with nothing more loaded:
	// asking again for a tile that could not be fetched is what has it fetched again, in time.
	// Sampling it every second for that kept the view busy for as long as a tile was missing,
	// which without a network is for good.
	static final long MISSING_RETRY_DELAY_MS = 15_000;
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
	// Per ring, how far along the way from the centre to the observer it is seen from.
	private static final double[] VIEWPOINT_SHARE = new double[RING_COUNT];

	static {
		for (int ray = 0; ray < RAY_COUNT; ray++) {
			double azimuth = 2 * Math.PI * ray / RAY_COUNT;
			RAY_SIN[ray] = Math.sin(azimuth);
			RAY_COS[ray] = Math.cos(azimuth);
		}
		for (int ring = 0; ring < RING_COUNT; ring++) {
			double distance = RING_DISTANCES[ring];
			CURVATURE_DROP[ring] = (1 - REFRACTION) * distance * distance
					/ (2 * GeoUtils.EARTH_RADIUS_M);
			VIEWPOINT_SHARE[ring] = distance * distance
					/ (distance * distance + VIEWPOINT_HALFWAY_METERS * VIEWPOINT_HALFWAY_METERS);
		}
	}

	private final Supplier<ElevationSnapshot> snapshots;
	private final Sampler sampler;
	private final Runnable onSampled;
	// Per sample, indexed by ray * RING_COUNT + ring. NaN while unknown, as all are before the
	// first sampling, so that nothing is drawn until then.
	private float[] elevations = new float[RAY_COUNT * RING_COUNT];
	// What the sampling under way writes, off the main thread. It takes the place of the
	// elevations once the sampling is back, and they become the spare ones: the samples on
	// display are never half written, and no sampling allocates its own.
	private float[] spareElevations = new float[RAY_COUNT * RING_COUNT];
	private final float[] screenX = new float[RAY_COUNT * RING_COUNT];
	private final float[] screenY = new float[RAY_COUNT * RING_COUNT];
	private final boolean[] drawable = new boolean[RAY_COUNT * RING_COUNT];
	// Per sample past hidden terrain, where its ray comes back into view on the way to it, see
	// placeEmergence. NaN otherwise.
	private final float[] emergeX = new float[RAY_COUNT * RING_COUNT];
	private final float[] emergeY = new float[RAY_COUNT * RING_COUNT];
	// Per ray, the sample on the skyline, or -1.
	private final int[] skyline = new int[RAY_COUNT];
	private double centreLatitude = Double.NaN;
	private double centreLongitude = Double.NaN;
	// Ground elevation at the centre, NaN while unknown.
	private double centreElevation = Double.NaN;
	private boolean complete;
	private long sampledAtMillis;
	// The version of the terrain as of the last sampling, see update.
	private int terrainVersion;
	// The sampling under way, null when none. One that was given up on still keeps the next
	// from starting until it is back, as it may be writing the spare elevations until then.
	private Sampling running;
	// Whether an update came while a sampling was under way, and where the observer was then,
	// with the terrain's version, for the sampling to make once that one is back.
	private boolean updateWaiting;
	private double observerLatitude;
	private double observerLongitude;
	private int observerTerrainVersion;
	private long observerMillis;
	private boolean destroyed;
	// The camera of the last projection, see toScreen.
	private double sinAzimuth;
	private double cosAzimuth;
	private double sinPitch;
	private double cosPitch;
	private double focalX;
	private double focalY;
	private double viewCentreX;
	private double viewCentreY;

	/**
	 * @param snapshots gives the elevations to sample, as they are when asked, on the main thread
	 * @param sampler   runs the samplings off the main thread
	 * @param onSampled run on the main thread once a sampling is on display, to draw it
	 */
	public TerrainWireframe(Supplier<ElevationSnapshot> snapshots, Sampler sampler,
	                        Runnable onSampled) {
		this.snapshots = snapshots;
		this.sampler = sampler;
		this.onSampled = onSampled;
		Arrays.fill(elevations, Float.NaN);
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
	 * Has the terrain sampled again when the observer moved away from the centre of the samples,
	 * once more of the terrain loaded, and now and then while some of it is missing, see
	 * MISSING_RETRY_DELAY_MS. The samples before stay on display until the sampling is back,
	 * which onSampled tells, unless they are too far from the observer to be drawn for them, see
	 * MAX_VIEWPOINT_DISTANCE_METERS. While a sampling is under way, none starts: the update
	 * waits for it to be back, and the terrain is then sampled again if the observer and the
	 * terrain, as of the last update, still call for it.
	 *
	 * @param terrainVersion changes whenever the elevations may have changed
	 * @return whether a sampling started
	 */
	public boolean update(double latitude, double longitude, int terrainVersion,
	                      long nowMillis) {
		if (destroyed) {
			return false;
		}
		if (isSampled()
				&& metersFromCentre(latitude, longitude) >= MAX_VIEWPOINT_DISTANCE_METERS) {
			forget();
		}
		observerLatitude = latitude;
		observerLongitude = longitude;
		observerTerrainVersion = terrainVersion;
		observerMillis = nowMillis;
		if (running != null) {
			updateWaiting = true;
			return false;
		}
		return sampleIfNeeded();
	}

	/**
	 * To be called while the wireframe does not show: the sampling under way is given up on,
	 * and none starts until the next update, so that nothing is sampled for a hidden wireframe.
	 */
	public void hide() {
		updateWaiting = false;
		giveUp();
	}

	/**
	 * To be called when the screen of the wireframe is destroyed: the sampling under way is
	 * given up on and comes back to nobody, without a word to the snapshot it read or to
	 * onSampled, and no other starts. Until it is done, it holds on to nothing of the screen.
	 */
	public void destroy() {
		destroyed = true;
		if (running != null) {
			giveUp();
			running.owner = null;
			running = null;
		}
	}

	/**
	 * @return whether a sampling is on display. Before the first one, nothing is drawn.
	 */
	public boolean isSampled() {
		return !Double.isNaN(centreLatitude);
	}

	private void giveUp() {
		if (running != null) {
			running.givenUp = true;
		}
	}

	/** Lets go of the samples on display, as before the first sampling. */
	private void forget() {
		centreLatitude = Double.NaN;
		centreLongitude = Double.NaN;
		centreElevation = Double.NaN;
		Arrays.fill(elevations, Float.NaN);
	}

	/**
	 * Starts a sampling for where the observer is as of the last update, when that calls for
	 * one. Only while none is under way.
	 */
	private boolean sampleIfNeeded() {
		boolean moved = !isSampled()
				|| metersFromCentre(observerLatitude, observerLongitude) > RECENTRE_DISTANCE_METERS;
		long sinceSampled = observerMillis - sampledAtMillis;
		boolean terrainChanged = observerTerrainVersion != terrainVersion
				&& sinceSampled >= TERRAIN_RESAMPLE_DELAY_MS;
		boolean retry = !complete && sinceSampled >= MISSING_RETRY_DELAY_MS;
		if (!moved && !terrainChanged && !retry) {
			return false;
		}

		// The snapshot is taken here, on the main thread. It is no older than the terrain's
		// version as of the last update, so no change of the terrain goes unnoticed.
		Sampling sampling = new Sampling(this, moved ? observerLatitude : centreLatitude,
				moved ? observerLongitude : centreLongitude, observerTerrainVersion,
				observerMillis, snapshots.get(), spareElevations);
		running = sampling;
		sampler.run(sampling, sampling::handBack);
		return true;
	}

	/**
	 * Takes a sampling back on the main thread, and puts it on display unless it was given up
	 * on since. The samples it replaces were on display until now.
	 */
	private void finished(Sampling sampling) {
		running = null;
		if (!sampling.givenUp) {
			spareElevations = elevations;
			elevations = sampling.elevations;
			centreLatitude = sampling.latitude;
			centreLongitude = sampling.longitude;
			centreElevation = sampling.centreElevation;
			complete = sampling.complete;
			// As of when the snapshot was taken, which is how old the elevations are.
			sampledAtMillis = sampling.startedAtMillis;
			terrainVersion = sampling.terrainVersion;
			sampling.snapshot.applied();
			onSampled.run();
		}
		// The observer may have moved on, or more terrain loaded, while it ran.
		if (updateWaiting) {
			updateWaiting = false;
			sampleIfNeeded();
		}
	}

	private double metersFromCentre(double latitude, double longitude) {
		return Math.hypot(eastOfCentre(longitude), northOfCentre(latitude));
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
	 * {@link #isDrawable}, {@link #getScreenX}, {@link #getScreenY}, {@link #isEmerging} and
	 * {@link #getSkyline}.
	 *
	 * @param eyeElevation elevation of the observer's eye above sea level, in meters
	 * @param eyeHeight    height of the observer's eye above the ground, which the viewpoints
	 *                     between the centre and the observer keep, see VIEWPOINT_SHARE
	 * @param azimuthDeg   compass azimuth of the camera axis
	 * @param pitchDeg     angle of the camera axis above the horizontal
	 * @param focalX       horizontal distance in pixel between the projection centre and the view
	 * @param focalY       vertical distance in pixel between the projection centre and the view
	 * @param centreX      horizontal position of the view centre
	 * @param centreY      vertical position of the view centre
	 */
	public void project(double latitude, double longitude, double eyeElevation, double eyeHeight,
	                    double azimuthDeg, double pitchDeg, double focalX, double focalY,
	                    double centreX, double centreY) {
		double observerEast = eastOfCentre(longitude);
		double observerNorth = northOfCentre(latitude);
		// How much higher the ground is under the observer than at the centre.
		double groundRise = Double.isNaN(centreElevation)
				? 0 : eyeElevation - eyeHeight - centreElevation;
		sinAzimuth = Math.sin(Math.toRadians(azimuthDeg));
		cosAzimuth = Math.cos(Math.toRadians(azimuthDeg));
		sinPitch = Math.sin(Math.toRadians(pitchDeg));
		cosPitch = Math.cos(Math.toRadians(pitchDeg));
		this.focalX = focalX;
		this.focalY = focalY;
		viewCentreX = centreX;
		viewCentreY = centreY;

		for (int ray = 0; ray < RAY_COUNT; ray++) {
			skyline[ray] = -1;
			double highestSlope = Double.NEGATIVE_INFINITY;
			// The previous sample along the ray, relative to the eye, and whether it is hidden.
			boolean previousHidden = false;
			double previousEast = 0;
			double previousNorth = 0;
			double previousUp = 0;
			double previousHorizontal = 0;
			for (int ring = 0; ring < RING_COUNT; ring++) {
				int index = sampleIndex(ray, ring);
				drawable[index] = false;
				emergeX[index] = Float.NaN;
				if (Float.isNaN(elevations[index])) {
					previousHidden = false;
					continue;
				}

				// Relative to the eye at the viewpoint of the ring, in meters. The viewpoint stays
				// close enough to the centre for every sample to be ahead of it along its ray,
				// see MAX_VIEWPOINT_DISTANCE_METERS.
				double share = VIEWPOINT_SHARE[ring];
				double east = RING_DISTANCES[ring] * RAY_SIN[ray] - share * observerEast;
				double north = RING_DISTANCES[ring] * RAY_COS[ray] - share * observerNorth;
				double up = elevations[index] - CURVATURE_DROP[ring] - eyeElevation
						+ (1 - share) * groundRise;
				double horizontal = Math.sqrt(east * east + north * north);

				// Walking out along the ray, terrain is hidden by any closer terrain seen higher.
				double slope = up / horizontal;
				boolean hidden = slope < highestSlope - OCCLUSION_SLACK_METERS / horizontal;
				if (!hidden && previousHidden) {
					placeEmergence(previousEast, previousNorth, previousUp, previousHorizontal,
							east, north, up, horizontal, highestSlope, index);
				}
				if (slope > highestSlope) {
					highestSlope = slope;
					skyline[ray] = index;
				}
				previousHidden = hidden;
				previousEast = east;
				previousNorth = north;
				previousUp = up;
				previousHorizontal = horizontal;
				if (!hidden) {
					drawable[index] = toScreen(east, north, up, screenX, screenY, index);
				}
			}
		}
	}

	/**
	 * Where the ray comes back into view, on its way from a hidden sample to the next one seen
	 * above the closer terrain: where the line between them crosses the line of sight over that
	 * terrain. Otherwise, the line of the ray would only start at the sample seen, as far as a
	 * ring step above where the terrain shows. Both samples are about in line with the eye, so
	 * how far the line runs below the line of sight changes linearly between them.
	 *
	 * @param horizon slope of the line of sight over the closer terrain
	 */
	private void placeEmergence(double fromEast, double fromNorth, double fromUp,
	                            double fromHorizontal, double toEast, double toNorth, double toUp,
	                            double toHorizontal, double horizon, int index) {
		double fromBelow = horizon * fromHorizontal - fromUp;
		double toBelow = horizon * toHorizontal - toUp;
		// A sample only seen thanks to OCCLUSION_SLACK_METERS shows nothing more on the way.
		if (toBelow >= 0) {
			return;
		}
		double share = fromBelow / (fromBelow - toBelow);
		toScreen(fromEast + share * (toEast - fromEast), fromNorth + share * (toNorth - fromNorth),
				fromUp + share * (toUp - fromUp), emergeX, emergeY, index);
	}

	/**
	 * Projects a point with the camera of the last projection.
	 *
	 * @return whether the point is far enough in front of the camera to be projected, into the
	 * coordinates at the index
	 */
	private boolean toScreen(double east, double north, double up, float[] x, float[] y,
	                         int index) {
		double right = east * cosAzimuth - north * sinAzimuth;
		double along = north * cosAzimuth + east * sinAzimuth;
		double forward = along * cosPitch + up * sinPitch;
		if (forward < MIN_FORWARD * Math.sqrt(east * east + north * north + up * up)) {
			return false;
		}
		double down = along * sinPitch - up * cosPitch;
		x[index] = (float) (viewCentreX + focalX * right / forward);
		y[index] = (float) (viewCentreY + focalY * down / forward);
		return true;
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
	 * @return whether the line of the ray to the drawable sample starts where the ray comes back
	 * into view, at {@link #getEmergeX} and {@link #getEmergeY}, as the previous sample is hidden
	 */
	public boolean isEmerging(int index) {
		return drawable[index] && !Float.isNaN(emergeX[index]);
	}

	public float getEmergeX(int index) {
		return emergeX[index];
	}

	public float getEmergeY(int index) {
		return emergeY[index];
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

	/**
	 * The elevations as they are known at one time. Unlike the source they are taken from, they
	 * may be read off the main thread, from one thread at a time.
	 */
	public interface ElevationSnapshot extends ElevationSource {
		/**
		 * Called on the main thread once what was read is on display, for the elevations found
		 * missing to be looked for, as reading them from their source would have.
		 */
		void applied();
	}

	/** Runs the samplings off the main thread. */
	public interface Sampler {
		/**
		 * Runs the work off the main thread, then done on the main thread, which thereby sees
		 * all the work wrote.
		 */
		void run(Runnable work, Runnable done);
	}

	/**
	 * One sampling: where and of what, and what came of it. It runs off the main thread, which
	 * only reads what it wrote once it is back, see finished.
	 */
	private static final class Sampling implements Runnable {
		// Who the sampling comes back to, on the main thread, which alone reads and sets it:
		// nobody once the screen is destroyed, see destroy.
		private TerrainWireframe owner;
		private final double latitude;
		private final double longitude;
		private final int terrainVersion;
		private final long startedAtMillis;
		private final ElevationSnapshot snapshot;
		private final float[] elevations;
		// Ground elevation at the centre, NaN while unknown.
		private double centreElevation;
		private boolean complete;
		// Set on the main thread once the result is not wanted any more, for the sampling to
		// stop short. What it wrote by then is not used.
		private volatile boolean givenUp;

		Sampling(TerrainWireframe owner, double latitude, double longitude, int terrainVersion,
		         long startedAtMillis, ElevationSnapshot snapshot, float[] elevations) {
			this.owner = owner;
			this.latitude = latitude;
			this.longitude = longitude;
			this.terrainVersion = terrainVersion;
			this.startedAtMillis = startedAtMillis;
			this.snapshot = snapshot;
			this.elevations = elevations;
		}

		@Override
		public void run() {
			double metersPerDegreeLongitude =
					METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude));
			centreElevation = snapshot.getElevation(latitude, longitude, 0);
			complete = !Double.isNaN(centreElevation);
			for (int ray = 0; ray < RAY_COUNT && !givenUp; ray++) {
				for (int ring = 0; ring < RING_COUNT; ring++) {
					double distance = RING_DISTANCES[ring];
					float elevation = (float) snapshot.getElevation(
							latitude + distance * RAY_COS[ray] / METERS_PER_DEGREE,
							longitude + distance * RAY_SIN[ray] / metersPerDegreeLongitude,
							distance);
					elevations[sampleIndex(ray, ring)] = elevation;
					complete &= !Float.isNaN(elevation);
				}
			}
		}

		/** Gives the sampling back once it ran, on the main thread. */
		void handBack() {
			if (owner != null) {
				owner.finished(this);
			}
		}
	}
}
