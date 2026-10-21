package com.climbtheworld.app.augmentedreality.ARCore;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
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
import com.climbtheworld.app.augmentedreality.placement.CursorHit;
import com.climbtheworld.app.augmentedreality.placement.GeoRegistration;
import com.climbtheworld.app.augmentedreality.placement.ViewRay;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
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
import java.util.function.Consumer;

/**
 * An ARCore session drawn in a view of the app and tied to the map, which both the A.R. placement
 * in the node editor and the A.R. view run on, see ArPlacementController and ArViewController:
 * the camera, the rock as ARCore's depth sees it and a 3D cursor where the crosshair meets it, see
 * ArSessionRenderer, with the tools to aim with.
 * <p>
 * GPS and the compass tie ARCore's world to the map, see GeoRegistration. Align ties it to mapped
 * routes instead: the user aims at one and picks it, and what is placed or shown then lands right
 * next to them. Past the depth, the cursor follows the wall below; Sight takes a first sighting of
 * a spot, for the user to aim at it again from a few meters to the side, the cursor then being
 * where the two cross. On a phone that cannot measure depth, that crossing is the only cursor.
 * <p>
 * ARCore runs on Google Play Services for AR, which not every phone can run. The first time, it
 * tells the user about ARCore; it asks for the camera, and has ARCore installed or updated, when
 * needed. The host is told when it does not come to run.
 */
class ArCoreSession {
	private static final String TAG = ArCoreSession.class.getSimpleName();
	// Compass readings only count while the camera looks about level, as the azimuth of a camera
	// looking steeply up or down means little, ...
	private static final double MAX_HEADING_PITCH_DEGREES = 60;
	// ... and while the phone turns slowly, as the compass lags behind ARCore.
	private static final double MAX_HEADING_TURN_RATE_DEGREES_PER_SECOND = 20;
	private static final float DISABLED_ICON_ALPHA = 0.4f;

	private final AppCompatActivity activity;
	private final Host host;
	// Whether it only runs where ARCore can measure depth.
	private final boolean depthRequired;
	private final GLSurfaceView surfaceView;
	private final TextView statusHint;
	private final View crosshair;
	private final TextView cursorDistance;
	private final FloatingActionButton alignButton;
	// On the align button's corner.
	private final TextView alignedCount;
	private final FloatingActionButton sightButton;
	private final ArSessionRenderer renderer;
	private final GeoRegistration registration = new GeoRegistration();
	private final AlignmentPicker alignmentPicker;
	// The mapped routes the user aimed at, each kept where they aimed by an anchor.
	private final List<AlignedRoute> alignedRoutes = new ArrayList<>();
	// Whether telling the host the camera moved is already posted to the main thread, which
	// reads where it is then.
	private final AtomicBoolean cameraMovePosted = new AtomicBoolean();
	private final Runnable cameraMoved = this::cameraMoved;
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
	// Whether the session measures depth.
	private boolean depthEnabled;
	private AlertDialog noticeDialog;
	// ARCore's install screen is showing; starting goes on when the activity resumes.
	private boolean installRequested;
	// The A.R. is to show, and it can run.
	private boolean shown;
	private boolean resumed;
	// The session and the view drawing it are running.
	private boolean running;
	// A first sighting is taken, see sight.
	private boolean sighting;
	// As the renderer last told them; null until it does.
	private TrackingState trackingState;
	private TrackingFailureReason trackingFailureReason;
	private CursorHit cursor;
	// A node not to align on, as the node being edited, 0 for none.
	private long leftOutNodeId;
	// The latest GPS fix, NaN until there is one.
	private double lastLatitude = Double.NaN;
	private double lastLongitude = Double.NaN;
	// 0 when unknown, see GeoNode.updatePOILocation.
	private double lastAltitude;

	/**
	 * To be made in the activity's onCreate, as it registers for the camera permission result.
	 *
	 * @param container     holds the R.id.arSurfaceView the session draws in, and its
	 *                      R.id.arStatusHint, R.id.arCrosshair, R.id.arCursorDistance,
	 *                      R.id.arAlignButton, R.id.arAlignedCount and R.id.arSightButton
	 * @param depthRequired whether it only runs where ARCore can measure depth
	 */
	ArCoreSession(AppCompatActivity activity, View container, boolean depthRequired, Host host) {
		this.activity = activity;
		this.host = host;
		this.depthRequired = depthRequired;
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.statusHint = container.findViewById(R.id.arStatusHint);
		this.crosshair = container.findViewById(R.id.arCrosshair);
		this.cursorDistance = container.findViewById(R.id.arCursorDistance);
		this.alignButton = container.findViewById(R.id.arAlignButton);
		this.alignedCount = container.findViewById(R.id.arAlignedCount);
		this.sightButton = container.findViewById(R.id.arSightButton);
		this.alignmentPicker = new AlignmentPicker(activity);
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
						host.onPinMoved();
					}

					@Override
					public void onCameraMoved() {
						if (cameraMovePosted.compareAndSet(false, true)) {
							activity.runOnUiThread(cameraMoved);
						}
					}

					@Override
					public void onTapped(CursorHit hit) {
						activity.runOnUiThread(() -> {
							if (running) {
								host.onTapped(hit);
							}
						});
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
						openSession();
					} else {
						host.onFailed(Failure.NO_CAMERA);
					}
				});

		alignButton.setOnClickListener(view -> align());
		sightButton.setOnClickListener(view -> sight());
	}

	/**
	 * Dims an icon button while it does nothing, which it does not show on its own.
	 */
	static void setIconEnabled(View button, boolean enabled) {
		button.setEnabled(enabled);
		button.setAlpha(enabled ? 1f : DISABLED_ICON_ALPHA);
	}

	/**
	 * Names an icon button, for accessibility, and in a tooltip on a long press.
	 */
	private static void describe(View button, CharSequence text) {
		button.setContentDescription(text);
		button.setTooltipText(text);
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
	 * Tells, on the main thread, whether ARCore can run on this phone at all, as far as it knows
	 * before trying.
	 */
	void checkSupported(Consumer<Boolean> supported) {
		ArCoreApk.getInstance().checkAvailabilityAsync(activity.getApplicationContext(),
				availability -> activity.runOnUiThread(() ->
						supported.accept(availability.isSupported())));
	}

	/**
	 * Shows the A.R. once ARCore is installed and a session is open: the first time, it tells the
	 * user about ARCore first, and it asks for the camera when needed.
	 */
	void start() {
		if (shown) {
			return;
		}
		if (Configs.instance(activity).getBoolean(Configs.ConfigKey.showArCoreNotice)) {
			showNotice();
		} else {
			requestCamera();
		}
	}

	void stop() {
		if (!shown) {
			return;
		}
		shown = false;
		apply();
	}

	/**
	 * @return whether the A.R. is to show; it runs while the activity is resumed
	 */
	boolean isShown() {
		return shown;
	}

	boolean isRunning() {
		return running;
	}

	boolean isTracking() {
		return running && trackingState == TrackingState.TRACKING;
	}

	/**
	 * @return whether the crosshair is on something known, as last told
	 */
	boolean isAiming() {
		return isTracking() && cursor != null;
	}

	/**
	 * @return whether ARCore's world is tied to the map
	 */
	boolean isReady() {
		return registration.isReady();
	}

	/**
	 * @return where the crosshair meets the rock now, null where nothing is known of it
	 */
	CursorHit getCursor() {
		return renderer.getCursor();
	}

	/**
	 * Shows the rock's wireframe and the cursor, as at first, or hides them, the cursor then
	 * being nowhere.
	 */
	void setAimingShown(boolean shown) {
		renderer.setAimingShown(shown);
	}

	/**
	 * @param nodeId a node not to align on, as the node being edited, 0 for none
	 */
	void setLeftOutNode(long nodeId) {
		leftOutNodeId = nodeId;
	}

	/**
	 * Works out where a tap on the view meets the rock, for Host.onTapped.
	 *
	 * @param x in the view's pixels
	 * @param y in the view's pixels
	 */
	void tap(float x, float y) {
		renderer.tap(x, y);
	}

	/**
	 * Puts the pin, see getPinOnScreen, at a point of the session's world, or takes it away.
	 *
	 * @param point null to take it away
	 */
	void setPin(double[] point) {
		renderer.setAnchor(ArSessionRenderer.PIN_ANCHOR, point);
	}

	/**
	 * @return where the pin shows, null while it is not in view
	 */
	ArSessionRenderer.PinOnScreen getPinOnScreen() {
		return renderer.getPinOnScreen();
	}

	/**
	 * Drops the first sighting, once the spot it was taken of is placed.
	 */
	void endSighting() {
		sighting = false;
		renderer.setSighting(null);
		updateStatus();
	}

	/**
	 * Where a point of the session's world is on the map.
	 *
	 * @return its latitude and longitude, null until isReady
	 */
	double[] toLatLon(double[] point) {
		refreshLandmarks();
		return registration.toLatLon(point[0], point[2]);
	}

	/**
	 * Puts a camera where ARCore's is, and turns it the same way, as ARCore's world is tied to the
	 * map. Its altitude is the GPS one.
	 *
	 * @return false, leaving the camera as it is, until ARCore tracks and its world is tied to the
	 * map
	 */
	boolean poseCamera(VirtualCamera camera) {
		ArSessionRenderer.SessionCamera sessionCamera = renderer.getSessionCamera();
		refreshLandmarks();
		double[] latLon = running && sessionCamera != null
				? registration.toLatLon(sessionCamera.x(), sessionCamera.z()) : null;
		if (latLon == null) {
			return false;
		}
		camera.updatePOILocation(latLon[0], latLon[1], lastAltitude);
		double azimuth = sessionCamera.azimuthDegrees() + registration.getHeadingOffsetDegrees();
		camera.degAzimuth = ((azimuth % 360) + 360) % 360;
		camera.degPitch = -sessionCamera.pitchDegrees();
		camera.degRoll = sessionCamera.angles().rollDegrees;
		setAngleOfView(camera, sessionCamera);
		// ARCore's pose follows the screen already.
		camera.screenRotation = 0;
		return true;
	}

	/**
	 * Gives a camera the angle of view of what the view shows of ARCore's camera, for a camera
	 * that something else moves over it.
	 *
	 * @return false, leaving the camera as it is, while ARCore does not track
	 */
	boolean matchAngleOfView(VirtualCamera camera) {
		ArSessionRenderer.SessionCamera sessionCamera = renderer.getSessionCamera();
		if (!running || sessionCamera == null) {
			return false;
		}
		setAngleOfView(camera, sessionCamera);
		return true;
	}

	private static void setAngleOfView(VirtualCamera camera,
	                                   ArSessionRenderer.SessionCamera sessionCamera) {
		camera.angleOfViewDeg = new Vector2d(sessionCamera.angles().horizontalAngleOfViewDegrees,
				sessionCamera.angles().verticalAngleOfViewDegrees);
	}

	void onResume() {
		resumed = true;
		if (installRequested) {
			openSession();
		}
		apply();
	}

	void onPause() {
		resumed = false;
		apply();
	}

	void onDestroy() {
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
	 * Takes a compass reading, against which way ARCore has the camera look.
	 */
	void onOrientation(OrientationManager.OrientationEvent event) {
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
	void onLocation(double latitude, double longitude, double altitudeMeters,
	                double accuracyMeters) {
		lastLatitude = latitude;
		lastLongitude = longitude;
		lastAltitude = altitudeMeters;
		ArSessionRenderer.SessionCamera camera = renderer.getSessionCamera();
		if (!running || camera == null) {
			return;
		}
		registration.addLocation(latitude, longitude, accuracyMeters, camera.x(), camera.z());
		updateStatus();
	}

	/**
	 * Tells the user that the A.R. runs on Google Play Services for AR, as Google asks of apps
	 * using ARCore, before it is installed or used.
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
				.setNegativeButton(android.R.string.cancel,
						(dialog, which) -> host.onFailed(Failure.NOTICE_DECLINED))
				.setOnCancelListener(dialog -> host.onFailed(Failure.NOTICE_DECLINED))
				.show();
		// A tap beside it is no answer: the user may have meant another dialog under it.
		noticeDialog.setCanceledOnTouchOutside(false);
		((TextView) noticeDialog.findViewById(android.R.id.message))
				.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private void requestCamera() {
		if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
				== PackageManager.PERMISSION_GRANTED) {
			openSession();
		} else {
			cameraPermission.launch(Manifest.permission.CAMERA);
		}
	}

	/**
	 * Shows the A.R. once ARCore is installed and a session is open. ARCore's install screen, when
	 * it is missing or too old, pauses the activity, which comes back here when it resumes.
	 */
	private void openSession() {
		if (session == null) {
			try {
				if (ArCoreApk.getInstance().requestInstall(activity, !installRequested)
						== ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
					installRequested = true;
					return;
				}
				installRequested = false;
				Session created = new Session(activity);
				// The cursor is placed on the depth ARCore measures, where it can.
				boolean depth = created.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
				if (!depth && depthRequired) {
					created.close();
					host.onFailed(Failure.NO_DEPTH);
					return;
				}
				if (depth) {
					Config config = created.getConfig();
					config.setDepthMode(Config.DepthMode.AUTOMATIC);
					created.configure(config);
				}
				session = created;
				depthEnabled = depth;
			} catch (UnavailableUserDeclinedInstallationException e) {
				installRequested = false;
				host.onFailed(Failure.INSTALL_DECLINED);
				return;
			} catch (UnavailableException | FatalException e) {
				Log.w(TAG, "ARCore can't run", e);
				installRequested = false;
				host.onFailed(Failure.UNSUPPORTED);
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
				host.onFailed(Failure.CAMERA_UNAVAILABLE);
				updateStatus();
				return;
			}
			// The session's world may be new after a pause.
			registration.reset();
			sighting = false;
			renderer.setSighting(null);
			trackingState = null;
			trackingFailureReason = null;
			cursor = null;
			renderer.setSession(session, depthEnabled);
			surfaceView.onResume();
			displayManager.registerDisplayListener(displayListener, null);
			running = true;
			host.onStarted();
		} else if (!run && running) {
			displayManager.unregisterDisplayListener(displayListener);
			surfaceView.onPause();
			renderer.setSession(null, false);
			session.pause();
			running = false;
		}
		updateStatus();
	}

	private void cameraMoved() {
		cameraMovePosted.set(false);
		host.onCameraMoved();
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
	 * Shows what the user is to do next, how far the cursor is, and what can be aimed with, then
	 * lets the host show the rest.
	 */
	void updateStatus() {
		refreshLandmarks();
		boolean tracking = isTracking();
		int hint = 0;
		if (running && !tracking) {
			hint = trackingMessage(trackingState, trackingFailureReason);
		} else if (tracking && !registration.hasLocation()) {
			hint = R.string.ar_placement_waiting_location;
		} else if (tracking && !registration.hasHeading()) {
			hint = R.string.ar_placement_waiting_heading;
		} else if (tracking && sighting && cursor == null) {
			hint = R.string.ar_placement_sight_move;
		} else if (tracking && cursor != null && cursor.source == CursorHit.Source.WALL_PLANE) {
			hint = R.string.ar_placement_on_wall_plane;
		} else if (tracking) {
			hint = host.idleHint();
		}
		statusHint.setVisibility(hint == 0 ? View.GONE : View.VISIBLE);
		if (hint != 0) {
			statusHint.setText(hint);
		}

		crosshair.setVisibility(running ? View.VISIBLE : View.GONE);
		boolean aiming = isAiming();
		cursorDistance.setVisibility(aiming ? View.VISIBLE : View.GONE);
		if (aiming) {
			cursorDistance.setText(distanceText(cursor));
		}
		// A mapped route is aimed at precisely, which the wall carried on is not.
		setIconEnabled(alignButton, aiming && cursor.source != CursorHit.Source.WALL_PLANE);
		describe(alignButton, alignedRoutes.isEmpty()
				? activity.getString(R.string.ar_placement_align)
				: activity.getString(R.string.ar_placement_align_count, alignedRoutes.size()));
		alignedCount.setVisibility(alignedRoutes.isEmpty() ? View.GONE : View.VISIBLE);
		alignedCount.setText(String.valueOf(alignedRoutes.size()));
		setIconEnabled(sightButton, tracking);
		sightButton.setImageResource(sighting ? R.drawable.ic_ar_sight_cancel
				: R.drawable.ic_ar_sight);
		describe(sightButton, activity.getString(sighting ? R.string.ar_placement_sight_cancel
				: R.string.ar_placement_sight));
		host.onStateChanged();
	}

	private String distanceText(CursorHit hit) {
		switch (hit.source) {
			case WALL_PLANE:
				return activity.getString(R.string.ar_placement_distance_wall, hit.distance);
			case SIGHTING:
				return activity.getString(R.string.ar_placement_distance_sighted, hit.distance,
						hit.uncertaintyMeters);
			default:
				return activity.getString(R.string.ar_placement_distance, hit.distance);
		}
	}

	/**
	 * Takes a first sighting along the crosshair, for the user to aim at the same spot again from
	 * a few meters to the side, or drops it.
	 */
	private void sight() {
		if (sighting) {
			sighting = false;
			renderer.setSighting(null);
		} else {
			ViewRay ray = renderer.getCrosshairRay();
			if (ray == null) {
				return;
			}
			sighting = true;
			renderer.setSighting(ray);
		}
		updateStatus();
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
		alignmentPicker.show(around[0], around[1], leftOutNodeId, !alignedRoutes.isEmpty(),
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

	/**
	 * Why the A.R. does not come to run, or stopped.
	 */
	enum Failure {
		// The user did not go on past the notice about ARCore.
		NOTICE_DECLINED,
		// The user did not let the app use the camera.
		NO_CAMERA,
		// The user did not let ARCore be installed or updated.
		INSTALL_DECLINED,
		// ARCore turned out not to run on this phone.
		UNSUPPORTED,
		// ARCore cannot measure depth on this phone, and depth is required.
		NO_DEPTH,
		// Another app has the camera.
		CAMERA_UNAVAILABLE
	}

	/**
	 * What the session tells the view it runs in, on the main thread unless told otherwise.
	 */
	interface Host {
		/**
		 * The session started running, and the camera shows, as after a pause too: what it shows
		 * may be a new world.
		 */
		void onStarted();

		/**
		 * The A.R. does not come to run, or stopped.
		 */
		void onFailed(Failure failure);

		/**
		 * The camera moved, while tracking, see poseCamera.
		 */
		void onCameraMoved();

		/**
		 * Something the status shows changed: whether the A.R. shows or runs, the tracking, the
		 * cursor or how ARCore's world is tied to the map.
		 */
		default void onStateChanged() {
		}

		/**
		 * @return what the user is to do once ARCore tracks, its world is tied to the map and
		 * nothing else is to be told, 0 for nothing
		 */
		default int idleHint() {
			return 0;
		}

		/**
		 * The pin moved in the view, or went out of it, see getPinOnScreen. On the GL thread.
		 */
		default void onPinMoved() {
		}

		/**
		 * The user tapped the view, see tap.
		 *
		 * @param hit where the tap meets the rock, null where nothing is known of it
		 */
		default void onTapped(CursorHit hit) {
		}
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
