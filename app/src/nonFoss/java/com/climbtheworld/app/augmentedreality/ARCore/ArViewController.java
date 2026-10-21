package com.climbtheworld.app.augmentedreality.ARCore;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ArViewTracking;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;

/**
 * The A.R. view on ARCore, on the phones it runs on: ARCore's camera image, with the rock as its
 * depth sees it, takes the place of the camera preview, and once ARCore's world is tied to the
 * map, its tracking moves and turns Globals.virtualCamera in place of GPS and the sensors, so the
 * A.R. view draws as it does, only steadier. Until then, and whenever ARCore loses track, GPS and
 * the sensors stand in, over ARCore's image. It runs on the same session as the A.R. placement in
 * the node editor, see ArCoreSession, with its Align and Sight. Where ARCore cannot measure depth,
 * it still runs, the cursor then only showing where the crosshair crosses a first sighting.
 * <p>
 * Elsewhere, with the Use ARCore setting off, or when the user does not let ARCore run, the A.R.
 * view goes on with CameraX and the sensors. Once refused, ARCore is left alone until the app
 * starts again, which asks once more; refused twice in a row, the setting is turned off, until the
 * user turns it on again.
 */
public class ArViewController implements ArViewTracking {
	private static final String TAG = ArViewController.class.getSimpleName();
	// How many refusals in a row turn the setting off.
	private static final int MAX_REFUSALS = 2;

	private final AppCompatActivity activity;
	private final Listener listener;
	// The camera image ARCore draws, behind the scene.
	private final View surfaceView;
	// The crosshair, the hint and the aiming buttons, over the scene.
	private final View controls;
	private final ArCoreSession arCore;
	private boolean resumed;
	// ARCore is to start once the activity is resumed, as in the node editor, where it starts
	// from a button: ARCore may show its own screens on the way.
	private boolean starting;
	// ARCore did not take the camera, or gave it back, for as long as the view is open.
	private boolean gaveUp;
	// Whether ARCore moves and turns the camera, see Listener.onMovesCameraChanged.
	private boolean movesCamera;

	/**
	 * To be made in the activity's onCreate, as it registers for the camera permission result.
	 *
	 * @param container the A.R. view's box: ARCore's camera image in R.id.arSurfaceView and the
	 *                  aiming controls in R.id.arCoreControls, see ArCoreSession
	 */
	public ArViewController(AppCompatActivity activity, View container, Listener listener) {
		this.activity = activity;
		this.listener = listener;
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.controls = container.findViewById(R.id.arCoreControls);
		this.arCore = new ArCoreSession(activity, container, false, new ArCoreSession.Host() {
			@Override
			public void onStarted() {
				Log.i(TAG, "ARCore runs");
				Configs.instance(activity).setInt(Configs.ConfigKey.arCoreViewRefusals, 0);
				surfaceView.setVisibility(View.VISIBLE);
				controls.setVisibility(View.VISIBLE);
				listener.onStarted();
			}

			@Override
			public void onFailed(ArCoreSession.Failure failure) {
				Log.i(TAG, "ARCore does not run: " + failure);
				switch (failure) {
					case NOTICE_DECLINED:
						refused();
						break;
					case INSTALL_DECLINED:
						refused();
						toast(R.string.ar_view_arcore_install_declined);
						break;
					case UNSUPPORTED:
						refused();
						toast(R.string.ar_view_arcore_unsupported);
						break;
					case CAMERA_UNAVAILABLE:
						toast(R.string.ar_placement_camera_unavailable);
						break;
					default:
						// The user did not let the app use the camera, which needs no telling.
						break;
				}
				giveUp();
			}

			@Override
			public void onCameraMoved() {
				// The sensors' camera shows over ARCore's image too.
				arCore.matchAngleOfView(Globals.virtualCamera);
				boolean posed = arCore.poseCamera(Globals.virtualCamera);
				setMovesCamera(posed);
				if (posed) {
					listener.onCameraMoved();
				}
			}

			@Override
			public void onStateChanged() {
				// Tracking lost, or the session's world no longer tied to the map.
				if (!(arCore.isTracking() && arCore.isReady())) {
					setMovesCamera(false);
				}
			}
		});
	}

	@Override
	public boolean start() {
		Configs configs = Configs.instance(activity);
		if (!configs.getBoolean(Configs.ConfigKey.useArCore)) {
			Log.i(TAG, "Not trying ARCore: the Use ARCore setting is off");
			gaveUp = true;
			return false;
		}
		if (configs.getBoolean(Configs.ConfigKey.arCoreViewRefusedThisRun)) {
			Log.i(TAG, "Not trying ARCore: refused since the app started");
			gaveUp = true;
			return false;
		}
		Log.i(TAG, "Trying ARCore once the view is resumed");
		starting = true;
		startIfResumed();
		return true;
	}

	@Override
	public void onResume() {
		resumed = true;
		arCore.onResume();
		startIfResumed();
	}

	@Override
	public void onPause() {
		resumed = false;
		arCore.onPause();
	}

	@Override
	public void onDestroy() {
		arCore.onDestroy();
	}

	@Override
	public void onOrientation(OrientationManager.OrientationEvent event) {
		arCore.onOrientation(event);
	}

	@Override
	public void onLocation(double latitude, double longitude, double altitudeMeters,
	                       double accuracyMeters) {
		arCore.onLocation(latitude, longitude, altitudeMeters, accuracyMeters);
	}

	private void startIfResumed() {
		if (!starting || !resumed) {
			return;
		}
		starting = false;
		Log.i(TAG, "Asking ARCore whether it supports this phone");
		arCore.checkSupported(supported -> {
			if (gaveUp || activity.isFinishing() || activity.isDestroyed()) {
				Log.i(TAG, "ARCore answered once the view no longer wanted it");
				return;
			}
			if (supported) {
				Log.i(TAG, "Starting ARCore");
				arCore.start();
			} else {
				Log.i(TAG, "ARCore does not support this phone");
				giveUp();
			}
		});
	}

	private void setMovesCamera(boolean moves) {
		if (moves == movesCamera) {
			return;
		}
		Log.i(TAG, moves ? "ARCore moves the camera" : "GPS and the sensors move the camera");
		movesCamera = moves;
		listener.onMovesCameraChanged(moves);
	}

	/**
	 * Counts a refusal of ARCore: the notice or the install declined, or ARCore not running.
	 */
	private void refused() {
		Configs configs = Configs.instance(activity);
		configs.setBoolean(Configs.ConfigKey.arCoreViewRefusedThisRun, true);
		int refusals = configs.getInt(Configs.ConfigKey.arCoreViewRefusals) + 1;
		if (refusals >= MAX_REFUSALS) {
			// For good, as the setting shows, and until the user turns it on again.
			configs.setBoolean(Configs.ConfigKey.useArCore, false);
			refusals = 0;
		}
		configs.setInt(Configs.ConfigKey.arCoreViewRefusals, refusals);
	}

	private void giveUp() {
		if (gaveUp) {
			return;
		}
		gaveUp = true;
		starting = false;
		arCore.stop();
		setMovesCamera(false);
		surfaceView.setVisibility(View.GONE);
		controls.setVisibility(View.GONE);
		listener.onGaveUp();
	}

	private void toast(int message) {
		Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
	}
}
