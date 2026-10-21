package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Display;

import com.climbtheworld.app.augmentedreality.placement.CursorHit;
import com.climbtheworld.app.augmentedreality.placement.CursorRing;
import com.climbtheworld.app.augmentedreality.placement.DepthFrame;
import com.climbtheworld.app.augmentedreality.placement.DepthWireframe;
import com.climbtheworld.app.augmentedreality.placement.LineVertices;
import com.climbtheworld.app.augmentedreality.placement.ScreenPoint;
import com.climbtheworld.app.augmentedreality.placement.RayCrossing;
import com.climbtheworld.app.augmentedreality.placement.SessionViewAngles;
import com.climbtheworld.app.augmentedreality.placement.ViewRay;
import com.climbtheworld.app.augmentedreality.placement.WallPlane;
import com.climbtheworld.app.utils.GeoUtils;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.SessionPausedException;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws each frame of an ARCore session on the GL thread of a GLSurfaceView: the camera image, the
 * rock as ARCore's depth sees it, and the 3D cursor where the crosshair, in the middle of the
 * view, meets the rock: on the depth, past it on the wall below carried on, or where the
 * crosshair crosses a first sighting. Works out the same for where the user taps the view. Tells when the tracking, the cursor or the placed pin changes, and keeps
 * where the camera, the cursor, the pin and the anchors are for the main thread, which draws the
 * pin.
 */
class ArSessionRenderer implements GLSurfaceView.Renderer {
	private static final String TAG = ArSessionRenderer.class.getSimpleName();
	private static final float NEAR_METERS = 0.1f;
	private static final float FAR_METERS = 100f;
	private static final float WIREFRAME_WIDTH_DP = 1f;
	private static final float CURSOR_WIDTH_DP = 2.5f;
	// How much of each new turn rate the smoothed one takes.
	private static final double TURN_RATE_SMOOTHING = 0.3;
	// The anchor of the placed pin.
	static final String PIN_ANCHOR = "pin";
	// An anchor request taking the anchor away.
	private static final double[] NO_ANCHOR = new double[0];

	private final Context context;
	private final Display display;
	private final Listener listener;
	private final float density;
	private final CameraBackground background = new CameraBackground();
	private final LineRenderer lines = new LineRenderer();
	private final DepthWireframe wireframe = new DepthWireframe();
	private final LineVertices wireframeLines = new LineVertices();
	private final LineVertices cursorLines = new LineVertices();
	private final LineVertices sightingLines = new LineVertices();
	private final float[] projection = new float[16];
	private final float[] view = new float[16];
	private final float[] viewProjection = new float[16];
	private final float[] cameraToWorld = new float[16];
	private final float[] viewCentre = new float[2];
	// Where the middle of the view is in the camera texture, from 0 to 1, NaN until known.
	private final float[] textureCentre = {Float.NaN, Float.NaN};
	// Set while the session runs, from the main thread.
	private volatile Session session;
	// Whether the session measures depth, which some phones cannot do: without it, there is no
	// wireframe of the rock, and the cursor only shows where the crosshair crosses a first sighting.
	private volatile boolean depthEnabled;
	// Set from the main thread: whether the rock's wireframe and the cursor show, and are worked
	// out at all.
	private volatile boolean aimingShown = true;
	// Set from the main thread when the display turns, which the view size does not show for a
	// half turn.
	private volatile boolean displayChanged;
	// Set from the main thread when the session starts running, so its tracking is told anew.
	private volatile boolean trackingReset;
	// Null while not tracking.
	private volatile SessionCamera sessionCamera;
	// Null while the crosshair is on nothing known.
	private volatile CursorHit cursor;
	// The ray through the crosshair, null while not tracking.
	private volatile ViewRay crosshairRay;
	// Set from the main thread: the first sighting, which the crosshair is aimed across, or null.
	private volatile ViewRay sighting;
	// Set from the main thread: where the user last tapped the view, in its pixels, null once
	// told.
	private final AtomicReference<float[]> pendingTap = new AtomicReference<>();
	// The rock last seen close by around the crosshair, carried on past the depth. GL thread only.
	private WallPlane wallPlane;
	// Null while the pin is not in view.
	private volatile PinOnScreen pinOnScreen;
	// Set from the main thread, by key: where to put an anchor, NO_ANCHOR to take it away.
	private final Map<String, double[]> anchorRequests = new ConcurrentHashMap<>();
	// Points ARCore keeps in place as it learns more of the world, by key. GL thread only.
	private final Map<String, Anchor> anchors = new HashMap<>();
	// Where the anchors ARCore tracks now are, by key.
	private volatile Map<String, double[]> anchorPositions = Collections.emptyMap();
	// The net over the rock is lined up with the way the user faces once tracking starts.
	private boolean wireframeFacingSet;
	// The session last given the camera texture and the display geometry, null to give them again.
	private Session configuredSession;
	private int viewWidth;
	private int viewHeight;
	private TrackingState trackingState;
	private TrackingFailureReason trackingFailureReason;
	// The cursor's distance and how sure it is, as last told, in decimeters, -1 for no cursor or
	// unknown, and where it came from.
	private long toldCursorDecimeters = -1;
	private long toldUncertainDecimeters = -1;
	private CursorHit.Source toldCursorSource;

	ArSessionRenderer(Context context, Display display, Listener listener) {
		this.context = context;
		this.display = display;
		this.listener = listener;
		this.density = context.getResources().getDisplayMetrics().density;
	}

	/**
	 * @param session the running session, null when it is paused
	 * @param depth   whether the session measures depth
	 */
	void setSession(Session session, boolean depth) {
		this.depthEnabled = depth;
		this.session = session;
		trackingReset = true;
	}

	void onDisplayChanged() {
		displayChanged = true;
	}

	/**
	 * @param shown whether to draw the rock's wireframe and the cursor; without them, there is no
	 *              cursor either, and no tap meets the rock
	 */
	void setAimingShown(boolean shown) {
		aimingShown = shown;
	}

	/**
	 * @return where the camera is and looks in the session's world, null while not tracking
	 */
	SessionCamera getSessionCamera() {
		return sessionCamera;
	}

	/**
	 * @return where the crosshair meets the rock, null where nothing is known of it
	 */
	CursorHit getCursor() {
		return cursor;
	}

	/**
	 * @return the ray through the crosshair, null while not tracking
	 */
	ViewRay getCrosshairRay() {
		return crosshairRay;
	}

	/**
	 * Works out where a tap on the view meets the rock, as the cursor is worked out for the
	 * crosshair, and tells it on the next frame, see Listener.onTapped. Taps while not tracking
	 * are dropped.
	 *
	 * @param x in the view's pixels
	 * @param y in the view's pixels
	 */
	void tap(float x, float y) {
		pendingTap.set(new float[]{x, y});
	}

	/**
	 * Aims the cursor across a first sighting, from the main thread, until set to null.
	 */
	void setSighting(ViewRay sighting) {
		this.sighting = sighting;
	}

	/**
	 * Puts an anchor at a point of the session's world, from the main thread; ARCore keeps it in
	 * place as it learns more of the world.
	 *
	 * @param key   PIN_ANCHOR, or any other
	 * @param point null to take the anchor away
	 */
	void setAnchor(String key, double[] point) {
		anchorRequests.put(key, point == null ? NO_ANCHOR : point.clone());
	}

	/**
	 * @return where the anchors are now in the session's world, by key, leaving out those ARCore
	 * does not track right now
	 */
	Map<String, double[]> getAnchorPositions() {
		return anchorPositions;
	}

	/**
	 * @return where the pin shows, null while it is not in view
	 */
	PinOnScreen getPinOnScreen() {
		return pinOnScreen;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		GLES20.glClearColor(0f, 0f, 0f, 1f);
		try {
			background.createOnGlThread(context);
			lines.createOnGlThread(context);
		} catch (IOException | RuntimeException e) {
			Log.e(TAG, "Could not create the GL programs", e);
		}
		// The texture is new.
		configuredSession = null;
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		GLES20.glViewport(0, 0, width, height);
		viewWidth = width;
		viewHeight = height;
		configuredSession = null;
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
		Session current = session;
		if (current == null || !background.isCreated()) {
			return;
		}
		if (trackingReset) {
			trackingReset = false;
			trackingState = null;
			trackingFailureReason = null;
			toldCursorDecimeters = -1;
			toldUncertainDecimeters = -1;
			toldCursorSource = null;
			sessionCamera = null;
			cursor = null;
			crosshairRay = null;
			// The session's world may be new.
			wallPlane = null;
			wireframeFacingSet = false;
		}
		if (displayChanged) {
			displayChanged = false;
			configuredSession = null;
		}
		if (current != configuredSession) {
			current.setCameraTextureName(background.getTextureId());
			current.setDisplayGeometry(display.getRotation(), viewWidth, viewHeight);
			configuredSession = current;
		}

		Frame frame;
		try {
			frame = current.update();
		} catch (CameraNotAvailableException e) {
			reportTracking(TrackingState.STOPPED, TrackingFailureReason.CAMERA_UNAVAILABLE);
			return;
		} catch (SessionPausedException e) {
			// Paused between the check and the update.
			return;
		}
		background.draw(frame);
		if (frame.hasDisplayGeometryChanged()) {
			viewCentre[0] = viewWidth / 2f;
			viewCentre[1] = viewHeight / 2f;
			frame.transformCoordinates2d(Coordinates2d.VIEW, viewCentre,
					Coordinates2d.TEXTURE_NORMALIZED, textureCentre);
		}

		Camera camera = frame.getCamera();
		updateAnchors(current);
		CursorHit hit = null;
		PinOnScreen pin = null;
		if (camera.getTrackingState() == TrackingState.TRACKING) {
			camera.getProjectionMatrix(projection, 0, NEAR_METERS, FAR_METERS);
			camera.getViewMatrix(view, 0);
			Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);
			updateSessionCamera(camera, frame.getTimestamp());
			if (!wireframeFacingSet) {
				wireframe.setFacing(sessionCamera.azimuthDegrees());
				wireframeFacingSet = true;
			}
			float[] tap = pendingTap.getAndSet(null);
			float[] tapTexture = null;
			if (tap != null) {
				tapTexture = new float[2];
				frame.transformCoordinates2d(Coordinates2d.VIEW, tap,
						Coordinates2d.TEXTURE_NORMALIZED, tapTexture);
			}
			boolean aiming = aimingShown;
			DepthHits depthHits = depthEnabled && aiming ? drawDepth(frame, camera, tapTexture)
					: new DepthHits(null, null);
			if (aiming) {
				hit = drawCursor(depthHits.centre(), camera);
			} else {
				crosshairRay = null;
			}
			if (tapTexture != null) {
				listener.onTapped(resolve(depthHits.tap(), rayThrough(camera, tapTexture)));
			}
			pin = projectPin();
		} else {
			sessionCamera = null;
			crosshairRay = null;
			pendingTap.set(null);
		}
		reportCursor(hit);
		reportPin(pin);
		reportTracking(camera.getTrackingState(), camera.getTrackingFailureReason());
	}

	/**
	 * Moves the anchors as the main thread asked, and tells where they are now.
	 */
	private void updateAnchors(Session current) {
		for (String key : new ArrayList<>(anchorRequests.keySet())) {
			double[] request = anchorRequests.remove(key);
			if (request == null) {
				continue;
			}
			Anchor previous = anchors.remove(key);
			if (previous != null) {
				previous.detach();
			}
			if (request == NO_ANCHOR) {
				continue;
			}
			try {
				anchors.put(key, current.createAnchor(Pose.makeTranslation((float) request[0],
						(float) request[1], (float) request[2])));
			} catch (IllegalStateException e) {
				// Not tracking right now: tried again on the next frame, unless asked otherwise.
				anchorRequests.putIfAbsent(key, request);
			}
		}

		if (anchors.isEmpty()) {
			anchorPositions = Collections.emptyMap();
			return;
		}
		Map<String, double[]> positions = new HashMap<>();
		for (Map.Entry<String, Anchor> entry : anchors.entrySet()) {
			if (entry.getValue().getTrackingState() == TrackingState.TRACKING) {
				Pose pose = entry.getValue().getPose();
				positions.put(entry.getKey(), new double[]{pose.tx(), pose.ty(), pose.tz()});
			}
		}
		anchorPositions = positions;
	}

	/**
	 * @return where the pin shows, null without a pin in view
	 */
	private PinOnScreen projectPin() {
		double[] point = anchorPositions.get(PIN_ANCHOR);
		if (point == null) {
			return null;
		}
		ScreenPoint screen = ScreenPoint.project(viewProjection, point, viewWidth, viewHeight);
		if (screen == null) {
			return null;
		}
		SessionCamera camera = sessionCamera;
		double distance = Math.sqrt(Math.pow(point[0] - camera.x(), 2)
				+ Math.pow(point[1] - camera.y(), 2) + Math.pow(point[2] - camera.z(), 2));
		return new PinOnScreen(screen.x, screen.y, screen.uprightDegrees, distance);
	}

	/**
	 * Draws the cursor where the crosshair meets the rock: where it crosses the first sighting,
	 * when there is one, or else on the depth, or else on the wall below carried on. Along with
	 * the first sighting's line, for the second one to be aimed across.
	 *
	 * @param depthHit where the crosshair is on the depth, null for nowhere
	 * @return the cursor, null where the crosshair is on nothing known
	 */
	private CursorHit drawCursor(CursorHit depthHit, Camera camera) {
		ViewRay ray = Float.isNaN(textureCentre[0]) ? null : rayThrough(camera, textureCentre);
		crosshairRay = ray;

		ViewRay first = sighting;
		if (first != null) {
			sightingLines.clear();
			CursorRing.sightingLine(first, sightingLines);
			lines.draw(viewProjection, sightingLines, CURSOR_WIDTH_DP * density);
		}
		CursorHit hit = resolve(depthHit, ray);
		if (hit != null) {
			cursorLines.clear();
			CursorRing.build(hit, cursorLines);
			lines.draw(viewProjection, cursorLines, CURSOR_WIDTH_DP * density);
		}
		return hit;
	}

	/**
	 * Where a ray through the crosshair, or a tap, meets the rock: where it crosses the first
	 * sighting, when there is one, or else on the depth, or else on the wall below carried on.
	 *
	 * @param depthHit where the ray is on the depth, null for nowhere
	 * @param ray      null when unknown
	 * @return null where the ray is on nothing known
	 */
	private CursorHit resolve(CursorHit depthHit, ViewRay ray) {
		ViewRay first = sighting;
		if (first != null) {
			return ray == null ? null : RayCrossing.cross(first, ray,
					wallPlane == null ? null : wallPlane.normal);
		}
		if (depthHit != null) {
			return depthHit;
		}
		return ray == null || wallPlane == null ? null : wallPlane.intersect(ray);
	}

	/**
	 * @param texture a point of the camera texture, from 0 to 1
	 * @return the ray through it, from the camera
	 */
	private ViewRay rayThrough(Camera camera, float[] texture) {
		CameraIntrinsics intrinsics = camera.getTextureIntrinsics();
		int[] textureSize = intrinsics.getImageDimensions();
		camera.getPose().toMatrix(cameraToWorld, 0);
		return ViewRay.throughTexture(texture[0] * textureSize[0], texture[1] * textureSize[1],
				intrinsics.getFocalLength(), intrinsics.getPrincipalPoint(), cameraToWorld);
	}

	/**
	 * Draws the rock's wireframe from the frame's depth, and keeps the wall plane around the
	 * crosshair while it is close.
	 *
	 * @param tapTexture where a tap is in the camera texture, from 0 to 1, null for none
	 * @return where the crosshair and the tap are on the depth, null where it is unknown
	 */
	private DepthHits drawDepth(Frame frame, Camera camera, float[] tapTexture) {
		try (Image depthImage = frame.acquireDepthImage16Bits()) {
			Image.Plane plane = depthImage.getPlanes()[0];
			ShortBuffer depth = plane.getBuffer().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			// The depth image shows what the camera texture shows, as the camera sensor sees it.
			CameraIntrinsics intrinsics = camera.getTextureIntrinsics();
			camera.getPose().toMatrix(cameraToWorld, 0);
			DepthFrame depthFrame = new DepthFrame(depth, depthImage.getWidth(),
					depthImage.getHeight(), plane.getRowStride() / Short.BYTES,
					intrinsics.getFocalLength(), intrinsics.getPrincipalPoint(),
					intrinsics.getImageDimensions(), cameraToWorld);

			wireframeLines.clear();
			wireframe.build(depthFrame, wireframeLines);
			lines.draw(viewProjection, wireframeLines, WIREFRAME_WIDTH_DP * density);

			CursorHit tapHit = tapTexture == null ? null : CursorHit.find(depthFrame,
					tapTexture[0] * depthImage.getWidth(), tapTexture[1] * depthImage.getHeight());
			if (Float.isNaN(textureCentre[0])) {
				return new DepthHits(null, tapHit);
			}
			double pixelX = textureCentre[0] * depthImage.getWidth();
			double pixelY = textureCentre[1] * depthImage.getHeight();
			CursorHit hit = CursorHit.find(depthFrame, pixelX, pixelY);
			if (hit != null && hit.distance <= WallPlane.MAX_SOURCE_METERS) {
				WallPlane fitted = WallPlane.fit(depthFrame, pixelX, pixelY);
				if (fitted != null) {
					wallPlane = fitted;
				}
			}
			return new DepthHits(hit, tapHit);
		} catch (NotYetAvailableException e) {
			// The first frames have no depth yet.
			return new DepthHits(null, null);
		} catch (IllegalStateException e) {
			// No longer tracking, or the frame is no longer the latest.
			return new DepthHits(null, null);
		}
	}

	private void updateSessionCamera(Camera camera, long timestampNanos) {
		Pose pose = camera.getPose();
		// The pose as the display shows it: its X axis along the screen's right.
		Pose displayPose = camera.getDisplayOrientedPose();
		SessionViewAngles angles = SessionViewAngles.of(displayPose.getXAxis(),
				displayPose.getYAxis(), displayPose.getZAxis(), projection);

		SessionCamera previous = sessionCamera;
		double turnRate = Double.NaN;
		if (previous != null && timestampNanos > previous.timestampNanos()) {
			double rate = Math.abs(GeoUtils.diffAngle(angles.azimuthDegrees,
					previous.angles().azimuthDegrees)) * 1e9
					/ (timestampNanos - previous.timestampNanos());
			turnRate = Double.isNaN(previous.turnRateDegreesPerSecond()) ? rate
					: previous.turnRateDegreesPerSecond()
					+ (rate - previous.turnRateDegreesPerSecond()) * TURN_RATE_SMOOTHING;
		}
		sessionCamera = new SessionCamera(pose.tx(), pose.ty(), pose.tz(), angles, turnRate,
				timestampNanos);
		listener.onCameraMoved();
	}

	private void reportCursor(CursorHit hit) {
		cursor = hit;
		// Told when it comes or goes, changes source, or when its distance or how sure it is
		// would show differently.
		long decimeters = hit == null ? -1 : Math.round(hit.distance * 10);
		long uncertainDecimeters = hit == null || Double.isNaN(hit.uncertaintyMeters) ? -1
				: Math.round(hit.uncertaintyMeters * 10);
		CursorHit.Source source = hit == null ? null : hit.source;
		if (decimeters != toldCursorDecimeters || uncertainDecimeters != toldUncertainDecimeters
				|| source != toldCursorSource) {
			toldCursorDecimeters = decimeters;
			toldUncertainDecimeters = uncertainDecimeters;
			toldCursorSource = source;
			listener.onCursorChanged(hit);
		}
	}

	private void reportPin(PinOnScreen pin) {
		// Told on every frame it shows, as the view moves, and once when it goes.
		boolean told = pin != null || pinOnScreen != null;
		pinOnScreen = pin;
		if (told) {
			listener.onPinMoved();
		}
	}

	private void reportTracking(TrackingState state, TrackingFailureReason reason) {
		if (state == trackingState && reason == trackingFailureReason) {
			return;
		}
		trackingState = state;
		trackingFailureReason = reason;
		listener.onTrackingChanged(state, reason);
	}

	/**
	 * Called on the GL thread.
	 */
	interface Listener {
		void onTrackingChanged(TrackingState state, TrackingFailureReason reason);

		/**
		 * @param hit the cursor, null when the crosshair is on no known depth
		 */
		void onCursorChanged(CursorHit hit);

		/**
		 * The pin moved in the view, or went out of it, see getPinOnScreen.
		 */
		void onPinMoved();

		/**
		 * The camera moved, while tracking, see getSessionCamera.
		 */
		void onCameraMoved();

		/**
		 * @param hit where the tap meets the rock, null where nothing is known of it
		 */
		void onTapped(CursorHit hit);
	}

	/**
	 * Where the crosshair and a tap are on the depth, each null where it is unknown or for none.
	 */
	private record DepthHits(CursorHit centre, CursorHit tap) {
	}

	/**
	 * Where the pin shows in the view.
	 *
	 * @param x               in view pixels, from the left
	 * @param y               in view pixels, from the top
	 * @param rotationDegrees clockwise, keeping the pin upright in the world
	 * @param distanceMeters  from the camera, which sets the pin's size
	 */
	record PinOnScreen(float x, float y, float rotationDegrees, double distanceMeters) {
	}

	/**
	 * Where the camera is in the session's world, and which way it looks.
	 *
	 * @param turnRateDegreesPerSecond how fast the azimuth changes, smoothed; NaN at first
	 */
	record SessionCamera(double x, double y, double z, SessionViewAngles angles,
	                     double turnRateDegreesPerSecond, long timestampNanos) {
		double azimuthDegrees() {
			return angles.azimuthDegrees;
		}

		double pitchDegrees() {
			return angles.pitchDegrees;
		}
	}
}
