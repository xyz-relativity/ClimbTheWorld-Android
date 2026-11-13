package com.climbtheworld.app.augmentedreality;

/**
 * Says when what is loaded around the observer is to be loaded again: once they moved some way
 * from where it was loaded, the view distance changed, or the data did. So what is loaded has to
 * reach at least that far beyond the view distance, for all in view to stay loaded in between.
 * One load runs at a time: a request made during it waits for its end.
 */
final class ReloadGuard {
	// How far the observer moves between two loads: a share of the view distance, plus meters.
	private final double share;
	private final double meters;
	// Where the last load was made around, nowhere before the first and once the data changed.
	private final LayoutOrigin loadedAround = new LayoutOrigin();
	private double loadedDistance;
	private boolean loading;
	// Whether a request was turned down, or the data changed, during the load.
	private boolean requestWaiting;
	private boolean changedWhileLoading;

	private ReloadGuard(double share, double meters) {
		this.share = share;
		this.meters = meters;
	}

	/**
	 * @param share the share of the view distance the observer moves between two loads
	 */
	static ReloadGuard afterShare(double share) {
		return new ReloadGuard(share, 0);
	}

	/**
	 * @param meters how far the observer moves between two loads, whatever the view distance
	 */
	static ReloadGuard afterMeters(double meters) {
		return new ReloadGuard(0, meters);
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
		if (loadedDistance == viewDistance && moved < viewDistance * share + meters) {
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
