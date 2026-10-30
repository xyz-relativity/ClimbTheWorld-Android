package com.climbtheworld.app.augmentedreality.ARCore;

import android.Manifest;
import android.app.AlertDialog;
import android.os.SystemClock;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AlignmentLinkView;
import com.climbtheworld.app.augmentedreality.AugmentedRealityScene;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.augmentedreality.placement.AlignedElevations;
import com.climbtheworld.app.augmentedreality.placement.AlignmentSuggestions;
import com.climbtheworld.app.augmentedreality.placement.CursorHit;
import com.climbtheworld.app.augmentedreality.placement.GeoRegistration;
import com.climbtheworld.app.augmentedreality.placement.ViewRay;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.widget.climbing.RouteWay;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.Anchor;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An ARCore session drawn in a view of the app and tied to the map, which both the A.R. placement
 * in the node editor and the A.R. view run on, see ArPlacementController and ArViewController:
 * the camera, the rock as ARCore's depth sees it and a 3D cursor where the crosshair meets it, see
 * ArSessionRenderer, with the tools to aim with.
 * <p>
 * GPS and the compass tie ARCore's world to the map, see GeoRegistration. Align ties it to mapped
 * routes instead: the user picks a route, brings its pin, riding on the cursor, from where the
 * A.R. has the route until then, which its pin drawn see-through shows, with a line between the
 * two, onto where the route starts, and drops it there, and what is placed or shown then lands
 * right next to it. Past the depth, the cursor follows the wall below. In the node editor,
 * Triangulation fixes a spot too far for the depth: a first sighting of it, then a second from a
 * few meters to the side, the spot being where the two cross, as ARCore measures the step between
 * them. On a phone that cannot measure depth, that is the only cursor there.
 * <p>
 * ARCore runs on Google Play Services for AR, which not every phone can run. The first time, it
 * tells the user about ARCore; it asks for the camera, and has ARCore installed or updated, when
 * needed. The host is told when it does not come to run.
 * <p>
 * The world, with the routes aligned on, can be handed over to the next A.R. of the app to open,
 * see handOver and ArWorldHandoff, which takes it in place of a new session, see openSession: the
 * A.R. view hands it to the node editor's A.R. placement, opened from the view, and the editor
 * hands it back as it closes, see setHandsWorldBack.
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
	// The info icon on the align button's top corner, away from where Align is tapped, as on the
	// tools and download buttons, while it can be tapped and aligning on more routes would help,
	// see isHeadingGood: orange until a route is aligned on, as the A.R. is only roughly placed,
	// green after. Tapped, it shows how to align, see AlignmentHelp.
	private final ImageView alignNotice;
	// On the align button's bottom corner.
	private final TextView alignedCount;
	// The pin of the route being aligned on, riding on the cursor, see align.
	private final ImageView alignPin;
	// The line from the route's pin where the A.R. has the route, which the scene draws
	// see-through, to the one riding on the cursor, see align.
	private final AlignmentLinkView alignLink;
	// Where the scene draws the see-through pin, see setSeeThroughPin.
	private Supplier<View> seeThroughPin = () -> null;
	// Whether moving the align pin is already posted to the main thread, which reads where it is
	// then.
	private final AtomicBoolean alignPinMovePosted = new AtomicBoolean();
	private final Runnable moveAlignPin = this::moveAlignPin;
	// Back puts the route being aligned on away, see align.
	private final OnBackPressedCallback cancelAlignment = new OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			endAligning();
		}
	};
	// Only with triangulation, null otherwise.
	private final FloatingActionButton triangulateButton;
	private final ArSessionRenderer renderer;
	private final GeoRegistration registration = new GeoRegistration();
	private final AlignmentPicker alignmentPicker;
	// The mapped routes the user aimed at, each kept where they aimed by an anchor. ARCore keeps
	// the anchors through a pause, and tracks them again once it finds its way back to what it saw
	// before; after a walk away with the phone locked it does not, and the alignments are lost
	// until the user aligns again, see trackedAlignedRoutes.
	private final List<AlignedRoute> alignedRoutes = new ArrayList<>();
	// The anchors of the routes aligned on ARCore tracked last, see cameraMoved.
	private Set<String> trackedAnchors = Collections.emptySet();
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
	// Whether to hand the world over as the activity closes, or is made again, see
	// setHandsWorldBack.
	private boolean handsWorldBack;
	private AlertDialog noticeDialog;
	// ARCore's install screen is showing; starting goes on when the activity resumes.
	private boolean installRequested;
	// The A.R. is to show, and it can run.
	private boolean shown;
	private boolean resumed;
	// The session and the view drawing it are running.
	private boolean running;
	// How far the triangulation is, see triangulate.
	private TriangulationStep triangulationStep = TriangulationStep.NONE;
	// Aligning, see align: the list of the routes is open, ...
	private boolean pickingRoute;
	// ... or the route picked rides on the cursor; null otherwise.
	private GeoNode aligningRoute;
	// As the renderer last told them; null until it does.
	private TrackingState trackingState;
	private TrackingFailureReason trackingFailureReason;
	private CursorHit cursor;
	// A node not to align on, as the node being edited, 0 for none.
	private long leftOutNodeId;
	// The elevation the scene stands the routes aligned on at, null while unknown.
	private AugmentedRealityScene.PinElevations pinElevations;
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
	 *                      R.id.arAlignButton, R.id.arAlignNotice, R.id.arAlignedCount,
	 *                      R.id.arAlignLink and R.id.arAlignPin, and with triangulation,
	 *                      R.id.arTriangulateButton
	 * @param depthRequired whether it only runs where ARCore can measure depth
	 * @param triangulation whether the user can triangulate a spot too far for the depth, see
	 *                      triangulate
	 */
	ArCoreSession(AppCompatActivity activity, View container, boolean depthRequired,
	              boolean triangulation, Host host) {
		this.activity = activity;
		this.host = host;
		this.depthRequired = depthRequired;
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.statusHint = container.findViewById(R.id.arStatusHint);
		this.crosshair = container.findViewById(R.id.arCrosshair);
		this.cursorDistance = container.findViewById(R.id.arCursorDistance);
		this.alignButton = container.findViewById(R.id.arAlignButton);
		this.alignNotice = container.findViewById(R.id.arAlignNotice);
		this.alignedCount = container.findViewById(R.id.arAlignedCount);
		this.alignPin = container.findViewById(R.id.arAlignPin);
		this.alignLink = container.findViewById(R.id.arAlignLink);
		this.triangulateButton = triangulation
				? container.findViewById(R.id.arTriangulateButton) : null;
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
					public void onCursorPinMoved() {
						if (alignPinMovePosted.compareAndSet(false, true)) {
							activity.runOnUiThread(moveAlignPin);
						}
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
							if (!running) {
								return;
							}
							// A tap drops the route being aligned on there.
							if (aligningRoute != null) {
								drop(hit);
							} else {
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
		alignNotice.setOnClickListener(view -> AlignmentHelp.show(activity));
		activity.getOnBackPressedDispatcher().addCallback(activity, cancelAlignment);
		if (triangulateButton != null) {
			triangulateButton.setOnClickListener(view -> triangulate());
		}
	}

	/**
	 * Dims an icon button while it does nothing, which it does not show on its own.
	 */
	static void setIconEnabled(View button, boolean enabled) {
		button.setEnabled(enabled);
		button.setAlpha(enabled ? 1f : DISABLED_ICON_ALPHA);
	}

	/**
	 * Shows an animated drawable running, or hides it, stopping it.
	 */
	private static void setAnimating(ImageView view, boolean animating) {
		view.setVisibility(animating ? View.VISIBLE : View.GONE);
		Drawable drawable = view.getDrawable();
		if (drawable instanceof Animatable) {
			if (animating) {
				((Animatable) drawable).start();
			} else {
				((Animatable) drawable).stop();
			}
		}
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
		cancelAligning();
		shown = false;
		apply();
	}

	/**
	 * Puts the route being aligned on away, and closes the list of the routes, as when the aiming
	 * tools go.
	 */
	void cancelAligning() {
		if (!isAligning()) {
			return;
		}
		alignmentPicker.dismiss();
		endAligning();
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

	/**
	 * @return whether the user is aligning on a route, see align, while only the pins of the
	 * routes aligned on are to show, see getShownWhileAligning
	 */
	boolean isAligning() {
		return pickingRoute || aligningRoute != null;
	}

	/**
	 * @return while the user aligns, the OSM IDs of the routes aligned on, but the one riding on
	 * the cursor, whose pins alone the scene is to show; null when not aligning
	 */
	Set<Long> getShownWhileAligning() {
		if (!isAligning()) {
			return null;
		}
		Set<Long> shown = alignedIds();
		if (aligningRoute != null) {
			shown.remove(aligningRoute.osmID);
		}
		return shown;
	}

	/**
	 * @return while the user aligns, the OSM ID of the route riding on the cursor, whose pin the
	 * scene is to draw see-through where it has the route, see AugmentedRealityScene.showOnly;
	 * null while none does
	 */
	Long getSeeThroughNodeId() {
		return aligningRoute == null ? null : aligningRoute.osmID;
	}

	/**
	 * Gives where the scene draws the pin of the route being aligned on, see-through, where the
	 * A.R. has the route until it is dropped, for the line from it to the pin riding on the
	 * cursor, see align and AugmentedRealityScene.getSeeThroughPin.
	 */
	void setSeeThroughPin(Supplier<View> pin) {
		seeThroughPin = pin;
	}

	/**
	 * @param aligned the routes aligned on that count, see updateStatus
	 * @return whether they, as the map has them, set the heading well enough that aligning on
	 * more would help little, see AlignmentSuggestions.isHeadingGood
	 */
	private static boolean isHeadingGood(List<AlignedRoute> aligned) {
		if (aligned.isEmpty()) {
			return false;
		}
		AlignedRoute first = aligned.get(0);
		List<double[]> eastNorth = new ArrayList<>(aligned.size());
		for (AlignedRoute route : aligned) {
			eastNorth.add(AlignmentSuggestions.eastNorthMeters(route.latitude(),
					route.longitude(), first.latitude(), first.longitude()));
		}
		return AlignmentSuggestions.isHeadingGood(eastNorth);
	}

	private Set<Long> alignedIds() {
		Set<Long> ids = new HashSet<>();
		for (AlignedRoute aligned : alignedRoutes) {
			ids.add(aligned.osmId());
		}
		return ids;
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
	 * Gives the elevation the scene stands the routes aligned on at, for the camera's height to
	 * follow them, see poseCamera.
	 */
	void setPinElevations(AugmentedRealityScene.PinElevations pinElevations) {
		this.pinElevations = pinElevations;
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
	/** A detector handing the single taps on the view to {@link #tap}. */
	GestureDetector tapDetector(Context context) {
		return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDown(MotionEvent event) {
				return true;
			}

			@Override
			public boolean onSingleTapUp(MotionEvent event) {
				tap(event.getX(), event.getY());
				return true;
			}
		});
	}

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
	 * Drops the triangulation, once its spot is placed.
	 */
	void endTriangulation() {
		triangulationStep = TriangulationStep.NONE;
		renderer.setSighting(null);
		renderer.setTriangulated(null);
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
	 * map. Its altitude is the one given with the GPS fixes; aligned on routes whose ground the
	 * terrain tells, the eye's elevation follows them too, and the terrain is corrected around
	 * them, see AlignedElevations.
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
		camera.alignedElevations = alignedElevations(sessionCamera);
		return true;
	}

	/**
	 * @return what the routes aligned on tell of the elevations, null while they tell nothing
	 */
	private AlignedElevations alignedElevations(ArSessionRenderer.SessionCamera sessionCamera) {
		if (pinElevations == null || alignedRoutes.isEmpty()) {
			return null;
		}
		Map<String, double[]> positions = renderer.getAnchorPositions();
		List<AlignedElevations.AimedBase> bases = new ArrayList<>(alignedRoutes.size());
		for (AlignedRoute aligned : alignedRoutes) {
			double[] position = positions.get(aligned.anchorKey());
			if (position == null) {
				continue;
			}
			double modelElevation = pinElevations.getElevation(aligned.route(),
					Math.hypot(position[0] - sessionCamera.x(), position[2] - sessionCamera.z()));
			bases.add(new AlignedElevations.AimedBase(aligned.osmId(), aligned.latitude(),
					aligned.longitude(), position[1], modelElevation));
		}
		return AlignedElevations.of(sessionCamera.y(), bases);
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
		// Before the A.R. that takes it opens, which the activity's end, told later, may follow.
		if (handsWorldBack && activity.isFinishing()) {
			handOver();
		}
	}

	void onDestroy() {
		if (noticeDialog != null) {
			noticeDialog.dismiss();
		}
		alignmentPicker.dismiss();
		shown = false;
		apply();
		// Made again, as when the screen turns, the activity takes the world back.
		if (handsWorldBack) {
			handOver();
		}
		if (session != null) {
			session.close();
			session = null;
		}
	}

	/**
	 * @param handsBack whether to hand the world over, see handOver, as the activity closes, to
	 *                  the A.R. that opens next, or is made again, to the activity made again: the
	 *                  node editor's A.R. placement opened from the A.R. view, which starts over
	 *                  as the editor closes and takes the world back
	 */
	void setHandsWorldBack(boolean handsBack) {
		handsWorldBack = handsBack;
	}

	/**
	 * Hands the session, with its world and the routes aligned on, over to the next A.R. of the
	 * app to open, see ArWorldHandoff, and goes on without one: the A.R. is stopped, see stop,
	 * and starting it again makes a new session, unless the world is handed back by then. The
	 * routes aligned on whose anchor ARCore has not made yet, as it was not tracking, are left
	 * behind.
	 *
	 * @return whether there was a session to hand over, which measures depth, as the node
	 * editor's A.R. placement needs, else the session stays
	 */
	boolean handOver() {
		if (session == null || !depthEnabled) {
			return false;
		}
		stop();
		Set<String> keys = new HashSet<>();
		for (AlignedRoute aligned : alignedRoutes) {
			keys.add(aligned.anchorKey());
		}
		Map<String, Anchor> anchors = renderer.takeAnchors(keys);
		List<GeoNode> routes = new ArrayList<>();
		for (AlignedRoute aligned : alignedRoutes) {
			if (anchors.containsKey(aligned.anchorKey())) {
				routes.add(aligned.route());
			}
		}
		ArWorldHandoff.offer(new ArWorldHandoff.World(session, depthEnabled, anchors, routes));
		session = null;
		alignedRoutes.clear();
		trackedAnchors = Collections.emptySet();
		registration.reset();
		updateStatus();
		return true;
	}

	/**
	 * Takes a world handed over in place of a new session, see handOver.
	 */
	private void adopt(ArWorldHandoff.World world) {
		session = world.session();
		depthEnabled = world.depth();
		renderer.adoptAnchors(world.anchors());
		alignedRoutes.clear();
		for (GeoNode route : world.alignedRoutes()) {
			alignedRoutes.add(new AlignedRoute(route));
		}
		Log.i(TAG, "ARCore's world taken over, aligned on " + alignedRoutes.size()
				+ " route(s)");
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
				event.headingAccuracyDegrees, SystemClock.elapsedRealtimeNanos());
		if (registration.hasHeading() != hadHeading) {
			updateStatus();
		}
	}

	/**
	 * How far off the heading the camera is turned by may be, see poseCamera: as the landmarks
	 * tell it when they set the heading, see GeoRegistration.landmarksSetHeading, else as the
	 * compass readings taken have been sure lately, see OrientationManager.OrientationEvent's
	 * headingUncertaintyDegrees, whatever the compass's momentary state: ARCore holds the
	 * heading, the compass only sets it.
	 *
	 * @param event the latest compass reading
	 * @return in degrees, NaN when nothing tells
	 */
	double headingUncertaintyDegrees(OrientationManager.OrientationEvent event) {
		double uncertainty = registration.headingAccuracyDegrees();
		if (!registration.landmarksSetHeading()) {
			uncertainty = OrientationManager.worseUncertaintyDegrees(uncertainty,
					event.headingUncertaintyDegrees);
		}
		return uncertainty;
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
			// A world handed over, as from the A.R. view, is taken in place of a new one, as
			// ARCore is installed then.
			ArWorldHandoff.World handed = ArWorldHandoff.take();
			if (handed != null && !handed.depth() && depthRequired) {
				handed.session().close();
				handed = null;
			}
			if (handed != null) {
				adopt(handed);
			}
		}
		if (session == null) {
			try {
				if (ArCoreApk.getInstance().requestInstall(activity, !installRequested)
						== ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
					installRequested = true;
					return;
				}
				installRequested = false;
				// The application's context, as the session may outlive the activity, handed
				// over to the next A.R. to open.
				Session created = new Session(activity.getApplicationContext());
				// The cursor is placed on the depth ARCore measures, where it can.
				boolean depth = created.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
				if (!depth && depthRequired) {
					created.close();
					host.onFailed(Failure.NO_DEPTH);
					return;
				}
				Config config = created.getConfig();
				// ARCore's planes go unused: the cursor fits the wall from the depth itself. Found
				// anyway, they pile up over a walk around a crag, and each frame refits them all,
				// which slows ARCore down more and more.
				config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
				if (depth) {
					config.setDepthMode(Config.DepthMode.AUTOMATIC);
				}
				created.configure(config);
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
			triangulationStep = TriangulationStep.NONE;
			renderer.setSighting(null);
			renderer.setTriangulated(null);
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
		// ARCore finding anchors again, or losing them, as after a pause, changes what the status
		// shows of the alignment.
		Set<String> tracked = new HashSet<>();
		for (AlignedRoute aligned : trackedAlignedRoutes()) {
			tracked.add(aligned.anchorKey());
		}
		if (!tracked.equals(trackedAnchors)) {
			trackedAnchors = tracked;
			updateStatus();
		}
		host.onCameraMoved();
		// The host drew the scene, with the see-through pin where the A.R. has the route now.
		alignLink.update();
	}

	/**
	 * @return the routes aligned on whose anchor ARCore tracks now, which are the ones tying its
	 * world to the map; the others are lost until it finds its way back to them
	 */
	private List<AlignedRoute> trackedAlignedRoutes() {
		Map<String, double[]> positions = renderer.getAnchorPositions();
		List<AlignedRoute> tracked = new ArrayList<>();
		for (AlignedRoute aligned : alignedRoutes) {
			if (positions.containsKey(aligned.anchorKey())) {
				tracked.add(aligned);
			}
		}
		return tracked;
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
		CharSequence hintText = null;
		if (running && !tracking) {
			hint = trackingMessage(trackingState, trackingFailureReason);
		} else if (tracking && aligningRoute != null) {
			// Aligning is what ties the world to the map, which may not be done yet.
			hintText = activity.getString(R.string.ar_placement_align_drag,
					aligningRoute.getName());
		} else if (tracking && !registration.hasHeading()) {
			// Before the location, which the hint offers to align for: without a heading a
			// route aligned on would not tie the world, so Align waits for the compass.
			hint = R.string.ar_placement_waiting_heading;
		} else if (tracking && !registration.hasLocation()) {
			hint = R.string.ar_placement_waiting_location;
		} else if (tracking && triangulationStep == TriangulationStep.FIRST) {
			// The cursor shows once the crosshair crosses the first sighting.
			hint = cursor == null ? R.string.ar_placement_triangulate_move
					: R.string.ar_placement_triangulate_fix_hint;
		} else if (tracking && triangulationStep == TriangulationStep.FIXED) {
			hint = R.string.ar_placement_triangulated;
		} else if (tracking && cursor != null && cursor.source == CursorHit.Source.WALL_PLANE) {
			hint = triangulateButton != null ? R.string.ar_placement_on_wall_plane
					: R.string.ar_view_on_wall_plane;
		} else if (tracking) {
			hint = host.idleHint();
		}
		if (hint != 0) {
			hintText = activity.getText(hint);
		}
		statusHint.setVisibility(hintText == null ? View.GONE : View.VISIBLE);
		if (hintText != null) {
			statusHint.setText(hintText);
		}

		crosshair.setVisibility(running ? View.VISIBLE : View.GONE);
		boolean aiming = isAiming();
		cursorDistance.setVisibility(aiming ? View.VISIBLE : View.GONE);
		if (aiming) {
			cursorDistance.setText(distanceText(cursor));
		}
		// The alignments that count are the ones ARCore tracks: lost ones tie nothing to the map
		// until the user aligns again. While ARCore has lost track altogether, as when the phone
		// moves too fast, they all stay as they were, as they mostly come back at once.
		List<AlignedRoute> counted = tracking ? trackedAlignedRoutes() : alignedRoutes;
		if (aligningRoute != null) {
			alignButton.setImageResource(R.drawable.ic_ar_align_drop);
			describe(alignButton, activity.getString(R.string.ar_placement_align_drop));
			// A mapped route is aimed at precisely, which the wall carried on is not.
			setIconEnabled(alignButton, aiming && cursor.source != CursorHit.Source.WALL_PLANE);
		} else {
			alignButton.setImageResource(R.drawable.ic_ar_align);
			describe(alignButton, counted.isEmpty()
					? activity.getString(R.string.ar_placement_align)
					: activity.getString(R.string.ar_placement_align_count, counted.size()));
			// Not before the compass has told the heading: a single route aligned on fixes the
			// position only, and would not tie the world until it does, see GeoRegistration.
			setIconEnabled(alignButton, canAlign());
		}
		// The info icon draws the eye to Align while it can be tapped and aligning on more routes
		// would help: orange until the A.R. is aligned on a route, green until the routes aligned
		// on set the heading well enough; and again once the alignments are lost.
		alignNotice.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(activity,
				counted.isEmpty() ? android.R.color.holo_orange_dark
						: android.R.color.holo_green_light)));
		setAnimating(alignNotice, aligningRoute == null && canAlign() && !isHeadingGood(counted));
		alignedCount.setVisibility(counted.isEmpty() ? View.GONE : View.VISIBLE);
		alignedCount.setText(String.valueOf(counted.size()));
		if (triangulateButton != null) {
			setIconEnabled(triangulateButton, tracking);
			switch (triangulationStep) {
				case FIRST:
					triangulateButton.setImageResource(R.drawable.ic_ar_triangulate_first);
					describe(triangulateButton,
							activity.getString(R.string.ar_placement_triangulate_fix));
					break;
				case FIXED:
					triangulateButton.setImageResource(R.drawable.ic_ar_triangulate_fixed);
					describe(triangulateButton,
							activity.getString(R.string.ar_placement_triangulate_drop));
					break;
				default:
					triangulateButton.setImageResource(R.drawable.ic_ar_triangulate);
					describe(triangulateButton,
							activity.getString(R.string.ar_placement_triangulate));
					break;
			}
		}
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
	 * Triangulation, a step per tap: a first sighting along the crosshair; then, aimed at the same
	 * spot from a few meters to the side, the spot where the two cross, fixed for Place or Align
	 * to use, the cursor staying on it; then dropping it.
	 */
	private void triangulate() {
		switch (triangulationStep) {
			case NONE: {
				ViewRay ray = renderer.getCrosshairRay();
				if (ray == null) {
					return;
				}
				triangulationStep = TriangulationStep.FIRST;
				renderer.setSighting(ray);
				break;
			}
			case FIRST: {
				CursorHit crossing = renderer.getCursor();
				if (crossing == null || crossing.source != CursorHit.Source.SIGHTING) {
					Toast.makeText(activity, R.string.ar_placement_triangulate_no_crossing,
							Toast.LENGTH_LONG).show();
					return;
				}
				triangulationStep = TriangulationStep.FIXED;
				renderer.setSighting(null);
				renderer.setTriangulated(crossing);
				break;
			}
			default:
				triangulationStep = TriangulationStep.NONE;
				renderer.setTriangulated(null);
				break;
		}
		updateStatus();
	}

	/**
	 * Align, a step per tap: the list of the routes around opens, the pins hiding but those of the
	 * routes aligned on, see getShownWhileAligning; the route picked rides on the cursor, for the
	 * user to bring it onto where the route starts, from where the A.R. has it until then, which
	 * the scene shows with its pin drawn see-through, see getSeeThroughNodeId, a line going from
	 * that pin to the one on the cursor, see AlignmentLinkView; a tap on the button, or on that
	 * spot, drops it there, which ties ARCore's world to the route. Back puts it away.
	 */
	private void align() {
		if (aligningRoute != null) {
			drop(renderer.getCursor());
			return;
		}
		if (!canAlign()) {
			return;
		}
		refreshLandmarks();
		// Around where the user is, who aims once they have picked the route.
		ArSessionRenderer.SessionCamera camera = renderer.getSessionCamera();
		double[] around = camera == null ? null : registration.toLatLon(camera.x(), camera.z());
		if (around == null) {
			around = Double.isNaN(lastLatitude) ? new double[]{
					Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude}
					: new double[]{lastLatitude, lastLongitude};
		}
		pickingRoute = true;
		updateStatus();
		alignmentPicker.show(around[0], around[1], leftOutNodeId, alignedIds(),
				new AlignmentPicker.Listener() {
					@Override
					public void onPicked(GeoNode route) {
						pickingRoute = false;
						startDragging(route);
					}

					@Override
					public void onDeleted(GeoNode route) {
						deleteAlignment(route.osmID);
					}

					@Override
					public void onCleared() {
						clearAlignment();
					}

					@Override
					public void onCancelled() {
						endAligning();
					}
				});
	}

	/**
	 * @return whether the user can start aligning: ARCore tracks, the list of the routes is not
	 * open, and the compass has told the heading, without which a route aligned on would not
	 * tie the world, see GeoRegistration.hasHeading
	 */
	private boolean canAlign() {
		return isTracking() && !pickingRoute && registration.hasHeading();
	}

	/**
	 * Has the route picked ride on the cursor, for the user to drop it where it starts.
	 */
	private void startDragging(GeoNode route) {
		aligningRoute = route;
		AugmentedRealityViewManager.setIcon(alignPin,
				AugmentedRealityViewManager.createIcon(activity, new DisplayableGeoNode(route)));
		alignLink.show(seeThroughPin, alignPin, RouteWay.gradeColor(route.getTags()));
		renderer.setCursorPinShown(true);
		cancelAlignment.setEnabled(true);
		updateStatus();
	}

	/**
	 * Aligns on the route riding on the cursor where a hit is, the cursor's or a tap's.
	 *
	 * @param hit null where nothing is known of the rock
	 */
	private void drop(CursorHit hit) {
		// A mapped route is aimed at precisely, which the wall carried on is not.
		if (hit == null || hit.source == CursorHit.Source.WALL_PLANE) {
			Toast.makeText(activity, triangulateButton != null
					? R.string.ar_placement_align_too_far
					: R.string.ar_view_align_too_far, Toast.LENGTH_LONG).show();
			return;
		}
		GeoNode route = aligningRoute;
		endAligning();
		alignOn(route, hit.point.clone());
	}

	/**
	 * Puts the route being aligned on away, if any, and shows all the pins again.
	 */
	private void endAligning() {
		pickingRoute = false;
		aligningRoute = null;
		renderer.setCursorPinShown(false);
		alignPin.setVisibility(View.GONE);
		alignLink.hide();
		cancelAlignment.setEnabled(false);
		updateStatus();
	}

	private void moveAlignPin() {
		alignPinMovePosted.set(false);
		ArSessionRenderer.PinOnScreen onScreen = renderer.getCursorPinOnScreen();
		if (!running || aligningRoute == null || onScreen == null) {
			alignPin.setVisibility(View.GONE);
			alignLink.update();
			return;
		}
		alignPin.setVisibility(View.VISIBLE);
		AugmentedRealityViewManager.placePin(alignPin, onScreen.x(), onScreen.y(),
				onScreen.distanceMeters(), onScreen.rotationDegrees());
		alignLink.update();
	}

	private void alignOn(GeoNode route, double[] aimed) {
		alignedRoutes.removeIf(aligned -> aligned.osmId() == route.osmID);
		AlignedRoute aligned = new AlignedRoute(route);
		alignedRoutes.add(aligned);
		renderer.setAnchor(aligned.anchorKey(), aimed);
		Toast.makeText(activity, activity.getString(R.string.ar_placement_aligned,
				route.getName()), Toast.LENGTH_LONG).show();
		// A triangulated spot, now aligned on.
		if (triangulationStep == TriangulationStep.FIXED) {
			triangulationStep = TriangulationStep.NONE;
			renderer.setTriangulated(null);
		}
		updateStatus();
	}

	private void deleteAlignment(long osmId) {
		for (AlignedRoute aligned : alignedRoutes) {
			if (aligned.osmId() == osmId) {
				renderer.setAnchor(aligned.anchorKey(), null);
			}
		}
		alignedRoutes.removeIf(aligned -> aligned.osmId() == osmId);
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
	 * How far the triangulation is, see triangulate.
	 */
	private enum TriangulationStep {
		NONE,
		// The first sighting is taken: the cursor shows where the crosshair crosses it.
		FIRST,
		// The spot where the two cross is fixed, and the cursor stays on it.
		FIXED
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
	 * A mapped route the user aimed at.
	 */
	private record AlignedRoute(GeoNode route) {
		long osmId() {
			return route.osmID;
		}

		// Where the map has it.
		double latitude() {
			return route.decimalLatitude;
		}

		double longitude() {
			return route.decimalLongitude;
		}

		String anchorKey() {
			return "route:" + route.osmID;
		}
	}
}
