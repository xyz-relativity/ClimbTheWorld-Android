package com.climbtheworld.app.augmentedreality;

/**
 * Says when what is loaded around the observer is to be loaded again: once they moved a share of
 * the view distance from where it was loaded, the view distance changed, or the data did. So what
 * is loaded has to reach at least that share beyond the view distance, for all in view to stay
 * loaded in between. One load runs at a time: a request made during it waits for its end.
 */
final class ReloadGuard {
	private final double share;
	// Where the last load was made around, nowhere before the first and once the data changed.
	private final LayoutOrigin loadedAround = new LayoutOrigin();
	private double loadedDistance;
	private boolean loading;
	// Whether a request was turned down, or the data changed, during the load.
	private boolean requestWaiting;
	private boolean changedWhileLoading;

	/**
	 * @param share the share of the view distance the observer moves between two loads
	 */
	ReloadGuard(double share) {
		this.share = share;
	}

	/**
	 * @param viewDistance how far the observer sees, in meters
	 * @return whether to load around the position now; the caller then tells once it is loaded,
	 * see loaded
	 */
	boolean request(double latitude, double longitude, double viewDistance) {
		if (loading) {
			requestWaiting = true;
			return false;
		}
		// NaN, so not close, while loaded around nowhere.
		double moved = Math.hypot(loadedAround.eastOf(longitude), loadedAround.northOf(latitude));
		if (loadedDistance == viewDistance && moved < viewDistance * share) {
			return false;
		}
		loading = true;
		return true;
	}

	/**
	 * Takes the load a request started as done, around the position and for the view distance
	 * it was made with.
	 *
	 * @return whether to make a request again, with where the observer is by now: one waited for
	 * this load, or the data changed during it
	 */
	boolean loaded(double latitude, double longitude, double viewDistance) {
		loading = false;
		loadedDistance = viewDistance;
		if (changedWhileLoading) {
			// The load may have read the data from before the change.
			loadedAround.moveTo(Double.NaN, Double.NaN);
		} else {
			loadedAround.moveTo(latitude, longitude);
		}
		boolean again = requestWaiting || changedWhileLoading;
		requestWaiting = false;
		changedWhileLoading = false;
		return again;
	}

	/**
	 * Has the next request load again, however little the observer moved, for data changed
	 * since, like a route just saved.
	 */
	void dataChanged() {
		loadedAround.moveTo(Double.NaN, Double.NaN);
		changedWhileLoading = loading;
	}
}
