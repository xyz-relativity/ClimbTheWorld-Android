package com.climbtheworld.app.augmentedreality.ARCore;

import android.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AlignmentRange;
import com.climbtheworld.app.augmentedreality.ArViewTracking;
import com.climbtheworld.app.augmentedreality.AugmentedRealityScene;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The A.R. view on ARCore, on the phones it runs on: ARCore's camera image, with the rock as its
 * depth sees it, takes the place of the camera preview, and once ARCore's world is tied to the
 * map, its tracking moves and turns Globals.virtualCamera in place of GPS and the sensors, so the
 * A.R. view draws as it does, only steadier. Until then, and whenever ARCore loses track, GPS and
 * the sensors stand in, over ARCore's image. It runs on the same session as the A.R. placement in
 * the node editor, see ArCoreSession, with its Align, but not its Triangulation, which is for
 * placing nodes. Where ARCore cannot measure depth, it still runs, with no cursor to align with.
 * <p>
 * The tools to aim with, the crosshair, the cursor, the rock's wireframe and Align, show
 * only near mapped routes, see AlignmentRange, as farther away there is nothing to align on. There,
 * a button hides them, until the user shows them again or walks away.
 * <p>
 * Elsewhere, with the Use ARCore setting off, or when the user does not let ARCore run, the A.R.
 * view goes on with CameraX and the sensors. Once refused, ARCore is left alone until the app
 * starts again, which asks once more; refused twice in a row, the setting is turned off, until the
 * user turns it on again. Without ARCore installed, or with it too old, the view does the same,
 * and offers to install it, see offerInstall.
 * <p>
 * To add a node from the view, ARCore's world is handed over to the node editor's A.R. placement
 * as the view is paused for it, see handOver, and taken back, with the routes aligned on there
 * too, when the view starts over once the editor closes, see ArWorldHandoff.
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
	// Hides the aiming tools, or shows them again, near routes.
	private final FloatingActionButton toolsButton;
	private final ArCoreSession arCore;
	private final AlignmentRange range = new AlignmentRange();
	// The user hid the aiming tools, until they show them again, across runs of the app, see
	// Configs.ConfigKey.arCoreToolsHidden.
	private boolean toolsHidden;
	private boolean resumed;
	// ARCore is to start once the activity is resumed, as in the node editor, where it starts
	// from a button: ARCore may show its own screens on the way.
	private boolean starting;
	// ARCore did not take the camera, or gave it back, for as long as the view is open.
	private boolean gaveUp;
	// Whether ARCore moves and turns the camera, see Listener.onMovesCameraChanged.
	private boolean movesCamera;
	// The world is to be handed over as the view is paused, see handOver.
	private boolean handOverOnPause;
	// As the listener was last told, see Listener.onAligningChanged.
	private Set<Long> shownWhileAligning;
	private Long seeThroughNodeId;
	// Offers to install ARCore, see offerInstall.
	private AlertDialog installDialog;

	/**
	 * To be made in the activity's onCreate, as it registers for the camera permission result.
	 *
	 * @param container the A.R. view's box: ARCore's camera image in R.id.arSurfaceView, the
	 *                  aiming controls in R.id.arCoreControls, see ArCoreSession, and the button
	 *                  hiding them in R.id.arCoreToolsButton
	 */
	public ArViewController(AppCompatActivity activity, View container, Listener listener) {
		this.activity = activity;
		this.listener = listener;
		this.surfaceView = container.findViewById(R.id.arSurfaceView);
		this.controls = container.findViewById(R.id.arCoreControls);
		this.toolsButton = container.findViewById(R.id.arCoreToolsButton);
		this.arCore = new ArCoreSession(activity, container, false, false, new ArCoreSession.Host() {
			@Override
			public void onStarted() {
				Log.i(TAG, "ARCore runs");
				Configs.instance(activity).setInt(Configs.ConfigKey.arCoreViewRefusals, 0);
				surfaceView.setVisibility(View.VISIBLE);
				updateTools();
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
				Set<Long> shown = arCore.getShownWhileAligning();
				Long seeThrough = arCore.getSeeThroughNodeId();
				if (!Objects.equals(shown, shownWhileAligning)
						|| !Objects.equals(seeThrough, seeThroughNodeId)) {
					shownWhileAligning = shown;
					seeThroughNodeId = seeThrough;
					listener.onAligningChanged(shown, seeThrough);
				}
			}
		});
		// A tap drops the route being aligned on there, see ArCoreSession.align.
		GestureDetector taps = arCore.tapDetector(activity);
		surfaceView.setOnTouchListener((view, event) -> taps.onTouchEvent(event));
		toolsHidden = Configs.instance(activity).getBoolean(Configs.ConfigKey.arCoreToolsHidden);
		toolsButton.setOnClickListener(view -> {
			toolsHidden = !toolsHidden;
			Configs.instance(activity).setBoolean(Configs.ConfigKey.arCoreToolsHidden, toolsHidden);
			updateTools();
		});
		updateTools();
	}

	@Override
	public boolean start() {
		Configs configs = Configs.instance(activity);
		if (!configs.getBoolean(Configs.ConfigKey.useArCore)) {
			Log.i(TAG, "Not trying ARCore: the Use ARCore setting is off");
			gaveUp = true;
			ArWorldHandoff.discard();
			return false;
		}
		if (configs.getBoolean(Configs.ConfigKey.arCoreViewRefusedThisRun)) {
			Log.i(TAG, "Not trying ARCore: refused since the app started");
			gaveUp = true;
			ArWorldHandoff.discard();
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
		// While the view still shows, up to here, as the editor comes over it.
		if (handOverOnPause) {
			handOverOnPause = false;
			if (arCore.handOver()) {
				Log.i(TAG, "ARCore's world handed to the node editor");
				setMovesCamera(false);
				updateTools();
			}
		}
		arCore.onPause();
	}

	@Override
	public void handOver() {
		handOverOnPause = true;
	}

	@Override
	public void onDestroy() {
		if (installDialog != null) {
			installDialog.dismiss();
		}
		arCore.onDestroy();
	}

	@Override
	public void onOrientation(OrientationManager.OrientationEvent event) {
		arCore.onOrientation(event);
	}

	@Override
	public double headingUncertaintyDegrees(OrientationManager.OrientationEvent event) {
		return movesCamera ? arCore.headingUncertaintyDegrees(event) : Double.NaN;
	}

	@Override
	public void onLocation(double latitude, double longitude, double altitudeMeters,
	                       double accuracyMeters) {
		arCore.onLocation(latitude, longitude, altitudeMeters, accuracyMeters);
	}

	@Override
	public void setPinElevations(AugmentedRealityScene.PinElevations pinElevations) {
		arCore.setPinElevations(pinElevations);
	}

	@Override
	public void setSeeThroughPin(Supplier<View> pin) {
		arCore.setSeeThroughPin(pin);
	}

	@Override
	public void setNearestRouteMeters(double meters) {
		boolean wasNear = range.isNear();
		if (range.update(meters) == wasNear) {
			return;
		}
		Log.i(TAG, wasNear ? "Away from mapped routes" : "Near mapped routes");
		updateTools();
	}

	/**
	 * Shows the aiming tools while ARCore has the camera near mapped routes, unless the user hid
	 * them, and the button to hide them, or show them again, while near.
	 */
	private void updateTools() {
		boolean near = !gaveUp && arCore.isShown() && range.isNear();
		boolean shown = near && !toolsHidden;
		controls.setVisibility(shown ? View.VISIBLE : View.GONE);
		// Its depth work is left out with them, as is aligning.
		arCore.setAimingShown(shown);
		if (!shown) {
			arCore.cancelAligning();
		}
		toolsButton.setVisibility(near ? View.VISIBLE : View.GONE);
		// Washed out while they are hidden, as the GPS centering button.
		if (shown) {
			toolsButton.clearColorFilter();
		} else {
			toolsButton.setColorFilter(MapLibreMapWidget.BUTTON_OFF_TINT);
		}
		CharSequence description = activity.getString(shown ? R.string.ar_view_hide_tools
				: R.string.ar_view_show_tools);
		toolsButton.setContentDescription(description);
		toolsButton.setTooltipText(description);
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
			if (supported && ArCoreInstall.needsInstall(activity)) {
				Log.i(TAG, "ARCore is not installed, or too old");
				offerInstall();
				giveUp();
			} else if (supported) {
				Log.i(TAG, "Starting ARCore");
				arCore.start();
			} else {
				Log.i(TAG, "ARCore does not support this phone");
				giveUp();
			}
		});
	}

	/**
	 * Offers to install ARCore, missing or too old, as the view opens, until the user says not to
	 * show it again, as its other notices: from Google Play, or as the APK from Google's GitHub
	 * page, for phones without Google Play. The view goes on with the sensors, and takes ARCore
	 * once it is installed and the view opens again. It tells about ARCore as its notice does.
	 */
	private void offerInstall() {
		Configs configs = Configs.instance(activity);
		if (!configs.getBoolean(Configs.ConfigKey.showArCoreInstall)) {
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setCancelable(true)
				.setTitle(R.string.arcore_notice_title)
				.setMessage(Html.fromHtml(activity.getString(R.string.arcore_notice_message)
								+ "<br /><br />" + activity.getString(R.string.arcore_install_from),
						Html.FROM_HTML_MODE_LEGACY))
				.setNegativeButton(R.string.arcore_install_apk, (dialog, which) -> {
					configs.setBoolean(Configs.ConfigKey.showArCoreNotice, false);
					ArCoreInstall.openApkPage(activity);
				})
				.setNeutralButton(R.string.dont_show_again, (dialog, which) ->
						configs.setBoolean(Configs.ConfigKey.showArCoreInstall, false));
		if (ArCoreInstall.hasPlayStore(activity)) {
			builder.setPositiveButton(R.string.arcore_install_play_store, (dialog, which) -> {
				configs.setBoolean(Configs.ConfigKey.showArCoreNotice, false);
				ArCoreInstall.openPlayStore(activity);
			});
		}
		installDialog = builder.show();
		((TextView) installDialog.findViewById(android.R.id.message))
				.setMovementMethod(LinkMovementMethod.getInstance());
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
		// A world handed back from the editor would go untaken.
		ArWorldHandoff.discard();
		arCore.stop();
		setMovesCamera(false);
		surfaceView.setVisibility(View.GONE);
		updateTools();
		listener.onGaveUp();
	}

	private void toast(int message) {
		Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
	}
}
