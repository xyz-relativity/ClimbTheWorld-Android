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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.augmentedreality.placement.CursorHit;
import com.climbtheworld.app.augmentedreality.placement.GeoRegistration;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The A.R. placement in an editor's map box: a button swaps the map for the camera, as ARCore
 * tracks it, with the rock as ARCore's depth sees it and a 3D cursor where the crosshair meets
 * it. Place puts the node there, on the map as GPS and the compass tie ARCore's world to it, and
 * its pin there in the A.R., as the A.R. view draws pins and the editor's map the edited node.
 * Align ties ARCore's world to mapped routes instead: the user aims at one and picks it, and
 * what they place then lands right next to them.
 * <p>
 * ARCore is optional: it runs on Google Play Services for AR, which not every phone can run. The
 * button only shows on phones ARCore supports, and is taken away when ARCore turns out not to run
 * or not to measure depth there. The first time, it tells the user about ARCore; it asks for the
 * camera, and has ARCore installed or updated, when needed.
 */
public class ArPlacementController {
	private static final String TAG = ArPlacementController.class.getSimpleName();
	private static final String STATE_SHOWN = "arPlacementShown";
	// Compass readings only count while the camera looks about level, as the azimuth of a camera
	// looking steeply up or down means little, ...
	private static final double MAX_HEADING_PITCH_DEGREES = 60;
	// ... and while the phone turns slowly, as the compass lags behind ARCore.
	private static final double MAX_HEADING_TURN_RATE_DEGREES_PER_SECOND = 20;

	private final AppCompatActivity activity;
	private final PlaceListener placeListener;
	private final View mapLayer;
	private final View arLayer;
	private final GLSurfaceView surfaceView;
	private final TextView statusHint;
	private final View crosshair;
	private final TextView cursorDistance;
	private final View placeButton;
	private final Button alignButton;
	// The placed node's pin, not interactive, as the editor's map has it.
	private final ImageView pin;
	private final FloatingActionButton toggleButton;
	private final ArSessionRenderer renderer;
	private final GeoRegistration registration = new GeoRegistration();
	private final AlignmentPicker alignmentPicker;
	// The mapped routes the user aimed at, each kept where they aimed by an anchor.
	private final List<AlignedRoute> alignedRoutes = new ArrayList<>();
	// Whether moving the pin is already posted to the main thread, which reads where it is then.
	private final AtomicBoolean pinMovePosted = new AtomicBoolean();
	private final Runnable movePin = this::movePin;
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
	// As the renderer last told them; null until it does.
	private TrackingState trackingState;
	private TrackingFailureReason trackingFailureReason;
	private CursorHit cursor;
	// The node being edited, which is not to align on.
	private long editedNodeId;
	// The latest GPS fix, NaN until there is one.
	private double lastLatitude = Double.NaN;
	private double lastLongitude = Double.NaN;

	/**
	 * To be made in the activity's onCreate, as it registers for the camera permission result.
	 *
	 * @param container     the map box, with the map views in R.id.mapLayer and the A.R. ones in
	 *                      R.id.arPlacementLayer
	 * @param placeListener told where the user placed the node
	 */
	public ArPlacementController(AppCompatActivity activity, View container,
	                             PlaceListener placeListener) {
		this.activity = activity;
		this.placeListener = placeListener;
		this.mapLayer = container.findViewById(R.id.mapLayer);
		this.arLayer = container.findViewById(R.id.arPlacementLayer);
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.statusHint = container.findViewById(R.id.arStatusHint);
		this.crosshair = container.findViewById(R.id.arCrosshair);
		this.cursorDistance = container.findViewById(R.id.arCursorDistance);
		this.placeButton = container.findViewById(R.id.arPlaceButton);
		this.alignButton = container.findViewById(R.id.arAlignButton);
		this.alignmentPicker = new AlignmentPicker(activity);
		this.pin = container.findViewById(R.id.arPlacedPin);
		this.toggleButton = container.findViewById(R.id.arPlacementToggleButton);
		this.displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);

		renderer = new ArSessionRenderer(activity, activity.getWindowManager().getDefaultDisplay(),
				new ArSessionRenderer.Listener() {
					@Override
					public void onTrackingChanged(TrackingState state,
					                              TrackingFailureReason reason) {
						activity.runOnUiThread(() -> trackingChanged(state, reason));
					}

					@Override
					public void onCursorChanged(CursorHit hit) {
						activity.runOnUiThread(() -> cursorChanged(hit));
					}

					@Override
					public void onPinMoved() {
						if (pinMovePosted.compareAndSet(false, true)) {
							activity.runOnUiThread(movePin);
						}
					}
				});
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

		placeButton.setOnClickListener(view -> place());
		alignButton.setOnClickListener(view -> align());
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
		alignmentPicker.dismiss();
		shown = false;
		apply();
		if (session != null) {
			session.close();
			session = null;
		}
	}

	/**
	 * Draws the placed node's pin as the node is now: its type and grade.
	 */
	public void setNode(DisplayableGeoNode node) {
		editedNodeId = node.getGeoNode().osmID;
		AugmentedRealityViewManager.setIcon(pin,
				AugmentedRealityViewManager.createIcon(activity, node));
	}

	/**
	 * Takes the placed pin away, for a node moved some other way, whose place in the A.R. is not
	 * known.
	 */
	public void clearPin() {
		renderer.setAnchor(ArSessionRenderer.PIN_ANCHOR, null);
	}

	/**
	 * Takes a compass reading, against which way ARCore has the camera look.
	 */
	public void onOrientation(OrientationManager.OrientationEvent event) {
		ArSessionRenderer.SessionCamera camera = renderer.getSessionCamera();
		if (!running || camera == null
				|| Math.abs(camera.pitchDegrees()) > MAX_HEADING_PITCH_DEGREES
				|| !(camera.turnRateDegreesPerSecond() <= MAX_HEADING_TURN_RATE_DEGREES_PER_SECOND)
				|| event.compassStatus == OrientationManager.CompassStatus.DISTURBED) {
			return;
		}
		boolean hadHeading = registration.hasHeading();
		registration.addHeading(event.camera.x, camera.azimuthDegrees(),
				event.headingAccuracyDegrees);
		if (registration.hasHeading() != hadHeading) {
			updateStatus();
		}
	}

	/**
	 * Takes a GPS fix, against where ARCore has the phone.
	 */
	public void onLocation(double latitude, double longitude, double accuracyMeters) {
		lastLatitude = latitude;
		lastLongitude = longitude;
		ArSessionRenderer.SessionCamera camera = renderer.getSessionCamera();
		if (!running || camera == null) {
			return;
		}
		registration.addLocation(latitude, longitude, accuracyMeters, camera.x(), camera.z());
		updateStatus();
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
				Config config = created.getConfig();
				config.setDepthMode(Config.DepthMode.AUTOMATIC);
				created.configure(config);
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
			// The session's world may be new after a pause.
			registration.reset();
			trackingState = null;
			trackingFailureReason = null;
			cursor = null;
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
		toggleButton.setImageResource(shown ? R.drawable.ic_world_map : R.drawable.ic_view_topo_ar);
		toggleButton.setContentDescription(activity.getString(
				shown ? R.string.ar_placement_hide : R.string.ar_placement_show));
		updateStatus();
	}

	private void trackingChanged(TrackingState state, TrackingFailureReason reason) {
		if (!running) {
			return;
		}
		// Tracking stopped for good: the session's world will be a new one.
		if (state == TrackingState.STOPPED) {
			registration.reset();
		}
		trackingState = state;
		trackingFailureReason = reason;
		updateStatus();
	}

	private void cursorChanged(CursorHit hit) {
		if (!running) {
			return;
		}
		cursor = hit;
		updateStatus();
	}

	/**
	 * Shows what the user is to do next, how far the cursor is, and whether it can be placed.
	 */
	private void updateStatus() {
		refreshLandmarks();
		boolean tracking = running && trackingState == TrackingState.TRACKING;
		int hint = 0;
		if (running && !tracking) {
			hint = trackingMessage(trackingState, trackingFailureReason);
		} else if (tracking && !registration.hasLocation()) {
			hint = R.string.ar_placement_waiting_location;
		} else if (tracking && !registration.hasHeading()) {
			hint = R.string.ar_placement_waiting_heading;
		} else if (tracking && cursor == null) {
			hint = R.string.ar_placement_no_surface;
		}
		statusHint.setVisibility(hint == 0 ? View.GONE : View.VISIBLE);
		if (hint != 0) {
			statusHint.setText(hint);
		}

		crosshair.setVisibility(running ? View.VISIBLE : View.GONE);
		if (!running) {
			pin.setVisibility(View.GONE);
		}
		boolean aiming = tracking && cursor != null;
		cursorDistance.setVisibility(aiming ? View.VISIBLE : View.GONE);
		if (aiming) {
			cursorDistance.setText(activity.getString(R.string.ar_placement_distance,
					cursor.distance));
		}
		placeButton.setEnabled(aiming && registration.isReady());
		alignButton.setEnabled(aiming);
		alignButton.setText(alignedRoutes.isEmpty()
				? activity.getString(R.string.ar_placement_align)
				: activity.getString(R.string.ar_placement_align_count, alignedRoutes.size()));
	}

	private static int trackingMessage(TrackingState state, TrackingFailureReason reason) {
		if (state == null || reason == null) {
			return R.string.ar_tracking_starting;
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
	 * Puts the node where the cursor is, as the session's world is tied to the map.
	 */
	private void place() {
		refreshLandmarks();
		CursorHit hit = renderer.getCursor();
		double[] latLon = hit == null ? null : registration.toLatLon(hit.point[0], hit.point[2]);
		if (latLon == null) {
			return;
		}
		placeListener.onPlace(latLon[0], latLon[1]);
		renderer.setAnchor(ArSessionRenderer.PIN_ANCHOR, hit.point);
	}

	/**
	 * Asks which mapped route the cursor is on, to tie ARCore's world to it.
	 */
	private void align() {
		CursorHit hit = renderer.getCursor();
		if (hit == null) {
			return;
		}
		// Where the user aimed, kept as the phone may move while they pick.
		double[] aimed = hit.point.clone();
		refreshLandmarks();
		double[] around = registration.toLatLon(aimed[0], aimed[2]);
		if (around == null) {
			around = Double.isNaN(lastLatitude) ? new double[]{
					Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude}
					: new double[]{lastLatitude, lastLongitude};
		}
		alignmentPicker.show(around[0], around[1], editedNodeId, !alignedRoutes.isEmpty(),
				new AlignmentPicker.Listener() {
					@Override
					public void onPicked(GeoNode route) {
						alignOn(route, aimed);
					}

					@Override
					public void onCleared() {
						clearAlignment();
					}
				});
	}

	private void alignOn(GeoNode route, double[] aimed) {
		alignedRoutes.removeIf(aligned -> aligned.osmId() == route.osmID);
		AlignedRoute aligned = new AlignedRoute(route.osmID, route.decimalLatitude,
				route.decimalLongitude);
		alignedRoutes.add(aligned);
		renderer.setAnchor(aligned.anchorKey(), aimed);
		Toast.makeText(activity, activity.getString(R.string.ar_placement_aligned,
				route.getName()), Toast.LENGTH_LONG).show();
		updateStatus();
	}

	private void clearAlignment() {
		for (AlignedRoute aligned : alignedRoutes) {
			renderer.setAnchor(aligned.anchorKey(), null);
		}
		alignedRoutes.clear();
		updateStatus();
	}

	/**
	 * Gives the registration the routes aligned on, where ARCore has them now.
	 */
	private void refreshLandmarks() {
		Map<String, double[]> positions = renderer.getAnchorPositions();
		List<GeoRegistration.Landmark> landmarks = new ArrayList<>();
		for (AlignedRoute aligned : alignedRoutes) {
			double[] position = positions.get(aligned.anchorKey());
			if (position != null) {
				landmarks.add(new GeoRegistration.Landmark(position[0], position[2],
						aligned.latitude(), aligned.longitude()));
			}
		}
		registration.setLandmarks(landmarks);
	}

	private void movePin() {
		pinMovePosted.set(false);
		ArSessionRenderer.PinOnScreen onScreen = renderer.getPinOnScreen();
		if (!running || onScreen == null || pin.getDrawable() == null) {
			pin.setVisibility(View.GONE);
			return;
		}
		pin.setVisibility(View.VISIBLE);
		AugmentedRealityViewManager.placePin(pin, onScreen.x(), onScreen.y(),
				onScreen.distanceMeters(), onScreen.rotationDegrees());
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

	public interface PlaceListener {
		void onPlace(double latitude, double longitude);
	}

	/**
	 * A mapped route the user aimed at, and where the map has it.
	 */
	private record AlignedRoute(long osmId, double latitude, double longitude) {
		String anchorKey() {
			return "route:" + osmId;
		}
	}
}
