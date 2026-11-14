package com.climbtheworld.app.activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ScreenLoads;

import needle.UiRelatedTask;

/**
 * An activity drawing the map, which follows the activity's lifecycle and the device's position
 * and heading: the subclass makes the widget in onCreate, and starts its sensors after
 * super.onResume and stops them before super.onPause.
 * <p>
 * Its background tasks are of two kinds. A load, whose result is only for the screen, goes
 * through screenLoads and is cancelled with the screen. A task that stores something is a
 * WriteTask, which is never cancelled.
 */
abstract class MapWidgetActivity extends AppCompatActivity
		implements IOrientationListener, ILocationListener {
	protected MapLibreMapWidget mapWidget;
	/** The loads to cancel when the screen closes, see finish and ScreenLoads. */
	protected final ScreenLoads screenLoads = new ScreenLoads();

	/**
	 * A task that stores something. Its work is done even once the user has left the screen, as
	 * what was saved or deleted is still wanted. What follows it on the UI thread is for the
	 * screen, so it is left out once the activity is finishing or destroyed: there is nothing
	 * left to close, and a dialog shown on a destroyed activity throws.
	 */
	protected abstract class WriteTask<Result> extends UiRelatedTask<Result> {
		@Override
		protected final void thenDoUiRelatedWork(Result result) {
			if (isFinishing() || isDestroyed()) {
				return;
			}
			thenOnOpenScreen(result);
		}

		/** What follows the work on the UI thread, while the screen is open. */
		protected abstract void thenOnOpenScreen(Result result);
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		mapWidget.onOrientationChange(event.getAdjusted(), event);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude,
	                           double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
		mapWidget.onLocationChange(new MapCoordinate(pDecLatitude, pDecLongitude, pMetersAltitude));
	}

	@Override
	protected void onStart() {
		super.onStart();
		mapWidget.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);
		mapWidget.onResume();
	}

	@Override
	protected void onPause() {
		mapWidget.onPause();
		Globals.onPause(this);
		super.onPause();
	}

	@Override
	protected void onStop() {
		mapWidget.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapWidget.onLowMemory();
	}

	/**
	 * Closes the screen, and cancels its loads right away: onDestroy only comes once the next
	 * screen is up, sometimes seconds later, and until then a load would still fill the form,
	 * start further loads, or open a dialog or another screen from one that is closing. A
	 * WriteTask is no load, so what the user saved or deleted before leaving is still stored.
	 */
	@Override
	public void finish() {
		screenLoads.cancel();
		super.finish();
	}

	@Override
	protected void onDestroy() {
		// Again, for a screen the system closes without finish.
		screenLoads.cancel();
		mapWidget.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		mapWidget.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}
}
