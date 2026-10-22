package com.climbtheworld.app.augmentedreality.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The elevations the mapped routes the user aligned on tell, from where they aimed at each
 * route's base: ARCore knows how far above or below each aimed spot the phone is, and the scene
 * stands each route's pin at the elevation the terrain model gives the foot of its wall.
 * <p>
 * The eye's elevation is the terrain model's at the bases, raised by how far the phone is above
 * them, averaged over the routes known both ways. So the pins stand at the height they were aimed
 * at, and the eye follows the phone up and down as ARCore tracks it, as when climbing, or up a
 * sloped approach.
 * <p>
 * The terrain model smooths a wall's foot, so along a sloped foot it has the bases closer in
 * height than they are, and the eye averaged over them puts each aligned pin a little off. So
 * the aligned routes also correct the model: at each aligned route, by the difference between
 * the height aimed at and the model's, and at any other spot, by that difference interpolated
 * from the aligned routes around, the nearest counting most, see offsetMeters. Averaging the eye
 * keeps the corrections summing to zero: the model's level stays, only its slope is corrected.
 */
public final class AlignedElevations {
	// Closer than this to an aligned route, a spot takes its correction outright.
	private static final double SAME_SPOT_METERS = 0.1;
	private static final double METERS_PER_DEGREE = 6378137 * Math.PI / 180;

	private final double eyeElevationMeters;
	private final List<Correction> corrections;

	private AlignedElevations(double eyeElevationMeters, List<Correction> corrections) {
		this.eyeElevationMeters = eyeElevationMeters;
		this.corrections = corrections;
	}

	/**
	 * @param cameraY the phone's height in the session's world
	 * @param bases   the routes aligned on, as the session has them now
	 * @return what they tell, null while none is known both ways
	 */
	public static AlignedElevations of(double cameraY, List<AimedBase> bases) {
		List<AimedBase> known = new ArrayList<>();
		double sum = 0;
		for (AimedBase base : bases) {
			if (Double.isNaN(base.baseY()) || Double.isNaN(base.modelElevation())) {
				continue;
			}
			known.add(base);
			sum += base.modelElevation() + (cameraY - base.baseY());
		}
		if (known.isEmpty()) {
			return null;
		}
		double eye = sum / known.size();
		List<Correction> corrections = new ArrayList<>(known.size());
		for (AimedBase base : known) {
			double aimedElevation = eye - (cameraY - base.baseY());
			corrections.add(new Correction(base.osmId(), base.latitude(), base.longitude(),
					aimedElevation - base.modelElevation()));
		}
		return new AlignedElevations(eye, Collections.unmodifiableList(corrections));
	}

	/**
	 * @return the eye's elevation above sea level
	 */
	public double eyeElevationMeters() {
		return eyeElevationMeters;
	}

	/**
	 * How much to raise the terrain model's elevation at a spot: at an aligned route, so that its
	 * pin stands where it was aimed; elsewhere, as the aligned routes around have it, weighed by
	 * the inverse square of how far they are, so a spot between two of them follows the slope
	 * between them, and a spot far from all of them gets about their average, which is zero.
	 *
	 * @param osmId the OSM ID of the node at the spot, 0 for none
	 */
	public double offsetMeters(long osmId, double latitude, double longitude) {
		double metersPerDegreeLongitude = METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude));
		double sum = 0;
		double weights = 0;
		for (Correction correction : corrections) {
			if (osmId != 0 && correction.osmId() == osmId) {
				return correction.meters();
			}
			double north = (correction.latitude() - latitude) * METERS_PER_DEGREE;
			double east = (correction.longitude() - longitude) * metersPerDegreeLongitude;
			double distance = Math.hypot(north, east);
			if (distance < SAME_SPOT_METERS) {
				return correction.meters();
			}
			double weight = 1 / (distance * distance);
			sum += correction.meters() * weight;
			weights += weight;
		}
		return sum / weights;
	}

	/**
	 * A route aligned on, as the session has it now.
	 *
	 * @param osmId          the route's OSM ID
	 * @param baseY          the height of the spot aimed at in the session's world, NaN while the
	 *                       session does not know it
	 * @param modelElevation the elevation the terrain model stands the route's pin at, above sea
	 *                       level, NaN while unknown
	 */
	public record AimedBase(long osmId, double latitude, double longitude, double baseY,
	                        double modelElevation) {
	}

	/**
	 * How much the terrain model is off at an aligned route.
	 */
	private record Correction(long osmId, double latitude, double longitude, double meters) {
	}
}
