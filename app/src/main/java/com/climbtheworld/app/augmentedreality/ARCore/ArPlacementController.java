package com.climbtheworld.app.augmentedreality.ARCore;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FatalException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

/**
 * The A.R. placement in an editor's map box: a button swaps the map for the camera, as ARCore
 * tracks it.
 * <p>
 * ARCore is optional: it runs on Google Play Services for AR, which not every phone can run. The
 * button only shows on phones ARCore supports, and is taken away when ARCore turns out not to run
 * or not to measure depth there. The first time, it tells the user about ARCore; it asks for the
 * camera, and has ARCore installed or updated, when needed.
 */
public class ArPlacementController {
	private static final String TAG = ArPlacementController.class.getSimpleName();
	private static final String STATE_SHOWN = "arPlacementShown";

	private final AppCompatActivity activity;
	private final View mapLayer;
	private final View arLayer;
	private final GLSurfaceView surfaceView;
	private final TextView trackingHint;
	private final FloatingActionButton toggleButton;
	private final ArSessionRenderer renderer;
	private final ActivityResultLauncher<String> cameraPermission;
	private final DisplayManager displayManager;
	private final DisplayManager.DisplayListener displayListener =
			new DisplayManager.DisplayListener() {
				@Override
				public void onDisplayAdded(int displayId) {
				}

				@Override
				public void onDisplayRemoved(int displayId) {
				}

				@Override
				public void onDisplayChanged(int displayId) {
					renderer.onDisplayChanged();
				}
			};

	private Session session;
	private AlertDialog noticeDialog;
	// ARCore turned out not to run, or not to measure depth, on this phone.
	private boolean unavailable;
	// ARCore's install screen is showing; starting goes on when the activity resumes.
	private boolean installRequested;
	// The A.R. was showing before the activity was made again; it starts when the activity resumes.
	private boolean startOnResume;
	// The user switched to the A.R., and it can run.
	private boolean shown;
	private boolean resumed;
	// The session and the view drawing it are running.
	private boolean running;

	/**
	 * To be made in the activity's onCreate, as it registers for the camera permission result.
	 *
	 * @param container the map box, with the map views in R.id.mapLayer and the A.R. ones in
	 *                  R.id.arPlacementLayer
	 */
	public ArPlacementController(AppCompatActivity activity, View container) {
		this.activity = activity;
		this.mapLayer = container.findViewById(R.id.mapLayer);
		this.arLayer = container.findViewById(R.id.arPlacementLayer);
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.trackingHint = container.findViewById(R.id.arTrackingHint);
		this.toggleButton = container.findViewById(R.id.arPlacementToggleButton);
		this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);

		renderer = new ArSessionRenderer(activity, activity.getWindowManager().getDefaultDisplay(),
				(state, reason) -> activity.runOnUiThread(() -> showTracking(state, reason)));
		surfaceView.setPreserveEGLContextOnPause(true);
		surfaceView.setEGLContextClientVersion(2);
		surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
		surfaceView.setRenderer(renderer);
		surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

		cameraPermission = activity.registerForActivityResult(
				new ActivityResultContracts.RequestPermission(), granted -> {
					if (granted) {
						start();
					} else {
						toast(R.string.ar_placement_no_camera);
					}
				});

		toggleButton.setOnClickListener(view -> toggle());
		toggleButton.setVisibility(View.GONE);
		ArCoreApk.getInstance().checkAvailabilityAsync(activity.getApplicationContext(),
				availability -> activity.runOnUiThread(() -> {
					if (availability.isSupported() && !unavailable) {
						toggleButton.setVisibility(View.VISIBLE);
					}
				}));
		updateLayers();
	}

	/**
	 * Shows the A.R. again when it was showing before the activity was made again.
	 */
	public void restore(Bundle savedInstanceState) {
		startOnResume = savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWN);
	}

	public void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(STATE_SHOWN, shown || startOnResume);
	}

	public void onResume() {
		resumed = true;
		if (startOnResume) {
			startOnResume = false;
			requestCamera();
		} else if (installRequested) {
			start();
		}
		apply();
	}

	public void onPause() {
		resumed = false;
		apply();
	}

	public void onDestroy() {
		if (noticeDialog != null) {
			noticeDialog.dismiss();
		}
		shown = false;
		apply();
		if (session != null) {
			session.close();
			session = null;
		}
	}

	private void toggle() {
		if (shown) {
			shown = false;
			apply();
		} else if (Configs.instance(activity).getBoolean(Configs.ConfigKey.showArCoreNotice)) {
			showNotice();
		} else {
			requestCamera();
		}
	}

	/**
	 * Tells the user that the A.R. placement runs on Google Play Services for AR, as Google asks of
	 * apps using ARCore, before it is installed or used.
	 */
	private void showNotice() {
		noticeDialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.arcore_notice_title)
				.setMessage(Html.fromHtml(activity.getString(R.string.arcore_notice_message),
						Html.FROM_HTML_MODE_LEGACY))
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					Configs.instance(activity).setBoolean(Configs.ConfigKey.showArCoreNotice, false);
					requestCamera();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
		((TextView) noticeDialog.findViewById(android.R.id.message))
				.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private void requestCamera() {
		if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
				== PackageManager.PERMISSION_GRANTED) {
			start();
		} else {
			cameraPermission.launch(Manifest.permission.CAMERA);
		}
	}

	/**
	 * Shows the A.R. once ARCore is installed and a session is open. ARCore's install screen, when
	 * it is missing or too old, pauses the activity, which comes back here when it resumes.
	 */
	private void start() {
		if (session == null) {
			try {
				if (ArCoreApk.getInstance().requestInstall(activity, !installRequested)
						== ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
					installRequested = true;
					return;
				}
				installRequested = false;
				Session created = new Session(activity);
				// The cursor is placed on the depth ARCore measures.
				if (!created.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
					created.close();
					takeAwayButton(R.string.ar_placement_no_depth);
					return;
				}
				session = created;
			} catch (UnavailableUserDeclinedInstallationException e) {
				installRequested = false;
				toast(R.string.ar_placement_install_declined);
				return;
			} catch (UnavailableException | FatalException e) {
				Log.w(TAG, "ARCore can't run", e);
				installRequested = false;
				takeAwayButton(R.string.ar_placement_unsupported);
				return;
			}
		}
		shown = true;
		apply();
	}

	/**
	 * Runs the session while the A.R. shows and the activity is resumed.
	 */
	private void apply() {
		boolean run = shown && resumed && session != null;
		if (run && !running) {
			try {
				session.resume();
			} catch (CameraNotAvailableException | SecurityException e) {
				Log.w(TAG, "No camera for ARCore", e);
				shown = false;
				toast(R.string.ar_placement_camera_unavailable);
				updateLayers();
				return;
			}
			renderer.setSession(session);
			surfaceView.onResume();
			displayManager.registerDisplayListener(displayListener, null);
			running = true;
		} else if (!run && running) {
			displayManager.unregisterDisplayListener(displayListener);
			surfaceView.onPause();
			renderer.setSession(null);
			session.pause();
			running = false;
		}
		updateLayers();
	}

	private void updateLayers() {
		mapLayer.setVisibility(shown ? View.GONE : View.VISIBLE);
		arLayer.setVisibility(shown ? View.VISIBLE : View.GONE);
		if (!running) {
			trackingHint.setVisibility(View.GONE);
		}
		toggleButton.setImageResource(shown ? R.drawable.ic_world_map : R.drawable.ic_view_topo_ar);
		toggleButton.setContentDescription(activity.getString(
				shown ? R.string.ar_placement_hide : R.string.ar_placement_show));
	}

	private void showTracking(TrackingState state, TrackingFailureReason reason) {
		int message = running ? trackingMessage(state, reason) : 0;
		trackingHint.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
		if (message != 0) {
			trackingHint.setText(message);
		}
	}

	private static int trackingMessage(TrackingState state, TrackingFailureReason reason) {
		if (state == TrackingState.TRACKING) {
			return 0;
		}
		switch (reason) {
			case INSUFFICIENT_LIGHT:
				return R.string.ar_tracking_too_dark;
			case EXCESSIVE_MOTION:
				return R.string.ar_tracking_too_fast;
			case INSUFFICIENT_FEATURES:
				return R.string.ar_tracking_no_detail;
			case CAMERA_UNAVAILABLE:
				return R.string.ar_placement_camera_unavailable;
			default:
				return R.string.ar_tracking_starting;
		}
	}

	/**
	 * Takes the button away for as long as the editor is open, with why.
	 */
	private void takeAwayButton(int message) {
		unavailable = true;
		toggleButton.setVisibility(View.GONE);
		toast(message);
	}

	private void toast(int message) {
		Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
	}
}
