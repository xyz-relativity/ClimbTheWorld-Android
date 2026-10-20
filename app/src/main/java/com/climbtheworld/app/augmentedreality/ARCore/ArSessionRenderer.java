package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Display;

import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.SessionPausedException;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws each frame of an ARCore session on the GL thread of a GLSurfaceView, and tells when its
 * tracking changes.
 */
class ArSessionRenderer implements GLSurfaceView.Renderer {
	private static final String TAG = ArSessionRenderer.class.getSimpleName();

	private final Context context;
	private final Display display;
	private final TrackingListener trackingListener;
	private final CameraBackground background = new CameraBackground();
	// Set while the session runs, from the main thread.
	private volatile Session session;
	// Set from the main thread when the display turns, which the view size does not show for a
	// half turn.
	private volatile boolean displayChanged;
	// Set from the main thread when the session starts running, so its tracking is told anew.
	private volatile boolean trackingReset;
	// The session last given the camera texture and the display geometry, null to give them again.
	private Session configuredSession;
	private int viewWidth;
	private int viewHeight;
	private TrackingState trackingState;
	private TrackingFailureReason trackingFailureReason;

	ArSessionRenderer(Context context, Display display, TrackingListener trackingListener) {
		this.context = context;
		this.display = display;
		this.trackingListener = trackingListener;
	}

	/**
	 * @param session the running session, null when it is paused
	 */
	void setSession(Session session) {
		this.session = session;
		trackingReset = true;
	}

	void onDisplayChanged() {
		displayChanged = true;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		GLES20.glClearColor(0f, 0f, 0f, 1f);
		try {
			background.createOnGlThread(context);
		} catch (IOException | RuntimeException e) {
			Log.e(TAG, "Could not create the camera background", e);
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

		Camera camera = frame.getCamera();
		reportTracking(camera.getTrackingState(), camera.getTrackingFailureReason());
	}

	private void reportTracking(TrackingState state, TrackingFailureReason reason) {
		if (state == trackingState && reason == trackingFailureReason) {
			return;
		}
		trackingState = state;
		trackingFailureReason = reason;
		trackingListener.onTrackingChanged(state, reason);
	}

	interface TrackingListener {
		/**
		 * Called on the GL thread.
		 */
		void onTrackingChanged(TrackingState state, TrackingFailureReason reason);
	}
}
