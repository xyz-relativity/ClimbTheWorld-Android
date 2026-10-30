package com.climbtheworld.app.augmentedreality;

import android.view.View;

import com.climbtheworld.app.sensors.orientation.OrientationManager;

import java.util.Set;
import java.util.function.Supplier;

/**
 * ARCore's tracking for the A.R. view, as the A.R. view sees it: once the camera may be used, it
 * takes the camera on the phones ARCore runs on and shows what it sees. While it tracks and its
 * world is tied to the map, by GPS, the compass and the routes the user aligns on, it moves and
 * turns Globals.virtualCamera in place of GPS and the sensors, which stand in the rest of the
 * time. Near mapped routes, see AlignmentRange, it also shows its tools to aim at them and align
 * on them. Elsewhere, the A.R. view runs on CameraX and the sensors.
 * <p>
 * Only the non-FOSS build has it, as ARCore is not free software; see ArCoreFeatures, which each
 * build has its own of. Without it, nothing here does anything.
 */
public interface ArViewTracking {
	/**
	 * Tries to take the camera, once the user let the app use it.
	 *
	 * @return false when it never does, as in the FOSS build; otherwise the listener is told
	 * whether it does
	 */
	default boolean start() {
		return false;
	}

	default void onResume() {
	}

	default void onPause() {
	}

	default void onDestroy() {
	}

	/**
	 * Hands ARCore's world, with the routes aligned on, over to the node editor's A.R. placement
	 * about to open, see ArPlacement.show, as the view is paused for it, so that it need not be
	 * aligned again; nothing where ARCore does not run, or cannot measure depth, which the
	 * placement needs. The view is to start over once the editor closes: it runs without ARCore
	 * until then.
	 */
	default void handOver() {
	}

	/**
	 * Takes a compass reading.
	 */
	default void onOrientation(OrientationManager.OrientationEvent event) {
	}

	/**
	 * How far off the heading ARCore turns the camera by may be, as
	 * OrientationManager.OrientationEvent's headingUncertaintyDegrees has it for the sensors'.
	 *
	 * @param event the latest compass reading, see onOrientation
	 * @return in degrees; NaN while ARCore does not turn the camera, see
	 * Listener.onMovesCameraChanged, when the heading is the sensors', with their own
	 */
	default double headingUncertaintyDegrees(OrientationManager.OrientationEvent event) {
		return Double.NaN;
	}

	/**
	 * Takes a GPS fix.
	 */
	default void onLocation(double latitude, double longitude, double altitudeMeters,
	                        double accuracyMeters) {
	}

	/**
	 * @param meters how far the closest mapped route is, NaN while none is known
	 */
	default void setNearestRouteMeters(double meters) {
	}

	/**
	 * Gives the elevation the terrain model stands the routes aligned on at, for the eye's
	 * height to follow them, see Globals.virtualCamera.alignedElevations.
	 */
	default void setPinElevations(AugmentedRealityScene.PinElevations pinElevations) {
	}

	/**
	 * Gives where the scene draws the pin of the route being aligned on, see-through, where the
	 * A.R. has it until it is dropped, for the line from it to the pin riding on the cursor, see
	 * AugmentedRealityScene.getSeeThroughPin.
	 */
	default void setSeeThroughPin(Supplier<View> pin) {
	}

	/**
	 * Told on the main thread.
	 */
	interface Listener {
		/**
		 * ARCore has the camera, and shows what it sees, until onGaveUp. Told again whenever it
		 * starts running again, as after a pause.
		 */
		void onStarted();

		/**
		 * ARCore does not take the camera, or gave it back for good: the A.R. view runs on CameraX
		 * and the sensors.
		 */
		void onGaveUp();

		/**
		 * @param moves whether ARCore moves and turns Globals.virtualCamera from now on, rather
		 *              than GPS and the sensors
		 */
		void onMovesCameraChanged(boolean moves);

		/**
		 * ARCore moved and turned Globals.virtualCamera.
		 */
		void onCameraMoved();

		/**
		 * @param shownNodeIds     while the user aligns on a route, the routes aligned on so far,
		 *                         by OSM ID, whose pins alone are to show, see
		 *                         AugmentedRealityScene.showOnly; null when not aligning
		 * @param seeThroughNodeId the OSM ID of the route riding on the cursor, whose pin is to
		 *                         show see-through where the A.R. has it; null while none does
		 */
		void onAligningChanged(Set<Long> shownNodeIds, Long seeThroughNodeId);
	}
}
