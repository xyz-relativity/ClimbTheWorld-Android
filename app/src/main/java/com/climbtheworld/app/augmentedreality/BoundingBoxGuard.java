package com.climbtheworld.app.augmentedreality;

/**
 * Says when the POIs within the view distance of the camera are to be picked again, see
 * AugmentedRealityScene.updateBoundingBox: once the camera moved some way from where they were
 * picked, the view distance changed, or the POIs loaded did. Picking them walks every POI loaded,
 * and it is asked for on every frame of ARCore's camera, which moves centimeters from one to the
 * next, so that the same ones would be picked each time.
 */
final class BoundingBoxGuard {
	// How far the camera moves before the POIs around it are picked again, the box they are
	// picked in being that far off until then. The view distance is 500 m or more, so all that
	// shows of it is a pin right at its edge coming up a meter of the walk late. None stays too
	// long: every draw still leaves out those past the view distance.
	static final double REDO_METERS = 1;

	// Where the POIs were last picked around, nowhere before the first time and once the POIs
	// loaded changed.
	private final LayoutOrigin pickedAround = new LayoutOrigin();
	private double pickedDistance;

	/**
	 * @param viewDistance how far the camera sees, in meters
	 * @return whether to pick the POIs around the position now, which it then takes as done
	 */
	boolean request(double latitude, double longitude, double viewDistance) {
		// NaN, so not close, while picked around nowhere.
		double moved = Math.hypot(pickedAround.eastOf(longitude), pickedAround.northOf(latitude));
		if (pickedDistance == viewDistance && moved < REDO_METERS) {
			return false;
		}
		pickedAround.moveTo(latitude, longitude);
		pickedDistance = viewDistance;
		return true;
	}

	/**
	 * Has the next request pick the POIs again, however little the camera moved, as more of them
	 * are loaded.
	 */
	void poisChanged() {
		pickedAround.moveTo(Double.NaN, Double.NaN);
	}
}
