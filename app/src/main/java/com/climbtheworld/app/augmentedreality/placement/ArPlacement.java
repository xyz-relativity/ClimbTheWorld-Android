package com.climbtheworld.app.augmentedreality.placement;

import android.os.Bundle;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.orientation.OrientationManager;

/**
 * The A.R. placement in an editor's map box, as the editor sees it: it forwards its lifecycle,
 * the node and the readings, and is told where the user placed the node.
 * <p>
 * Only the non-FOSS build has it, as ARCore is not free software; see ArPlacements, which each
 * build has its own of. Without it, nothing here does anything, and the map box stays a map, as
 * on a phone ARCore does not run on.
 */
public interface ArPlacement {
	/**
	 * Shows the A.R. again when it was showing before the activity was made again.
	 */
	default void restore(Bundle savedInstanceState) {
	}

	default void onSaveInstanceState(Bundle outState) {
	}

	default void onStart() {
	}

	default void onResume() {
	}

	default void onPause() {
	}

	default void onStop() {
	}

	default void onLowMemory() {
	}

	default void onDestroy() {
	}

	/**
	 * Draws the placed node's pin as the node is now: its type and grade.
	 */
	default void setNode(DisplayableGeoNode node) {
	}

	/**
	 * Takes the placed pin away, for a node moved some other way, whose place in the A.R. is not
	 * known.
	 */
	default void clearPin() {
	}

	/**
	 * Takes a compass reading.
	 */
	default void onOrientation(OrientationManager.OrientationEvent event) {
	}

	/**
	 * Takes a GPS fix.
	 */
	default void onLocation(double latitude, double longitude, double altitudeMeters,
	                        double accuracyMeters) {
	}

	interface PlaceListener {
		void onPlace(double latitude, double longitude);
	}
}
