package com.climbtheworld.app.augmentedreality.ARCore;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityScene;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.augmentedreality.placement.ArPlacement;
import com.climbtheworld.app.augmentedreality.placement.CursorHit;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.views.ViewSwitchDrawable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The A.R. placement in an editor's map box: a button swaps the map for the camera, as ARCore
 * tracks it, with the rock as ARCore's depth sees it and a 3D cursor where the crosshair meets
 * it, see ArCoreSession. A tap puts the node where it meets the rock, as on the editor's map, and
 * the place button where the cursor is, on the map as ARCore's world is tied to it, and its pin
 * there in the A.R., as the A.R. view draws pins and the editor's map the edited node. Once
 * ARCore's world is tied to the map, the routes, crags and areas around show as the A.R. view
 * draws them, see AugmentedRealityScene.
 * <p>
 * ARCore is optional: the button only shows on phones ARCore supports, and is taken away when
 * ARCore turns out not to run or not to measure depth there.
 * <p>
 * Opened from the A.R. view, see show, the A.R. shows from the start, on ARCore's world as the
 * view had it tied to the map, with the routes aligned on, when the view handed it over, and
 * hands it back as the editor closes, see ArWorldHandoff.
 */
public class ArPlacementController implements ArPlacement {
	private static final String STATE_SHOWN = "arPlacementShown";
	private static final String STATE_HANDS_WORLD_BACK = "arPlacementHandsWorldBack";
	// How far the observer moves before the data around them is loaded again.
	private static final double SCENE_RELOAD_METERS = 10;

	private final AppCompatActivity activity;
	private final PlaceListener placeListener;
	private final View mapLayer;
	private final View arLayer;
	private final FloatingActionButton placeButton;
	// The placed node's pin, not interactive, as the editor's map has it.
	private final ImageView pin;
	private final FloatingActionButton toggleButton;
	// The map and the A.R., the one to switch to in the top circle.
	private final ViewSwitchDrawable toggleIcon;
	private final ArCoreSession arCore;
	// Whether moving the pin is already posted to the main thread, which reads where it is then.
	private final AtomicBoolean pinMovePosted = new AtomicBoolean();
	private final Runnable movePin = this::movePin;
	// The camera ARCore tracks, as the scene sees it: where the session's world puts it on Earth.
	private final VirtualCamera sceneCamera = new VirtualCamera(0, 0, 0);
	private final AugmentedRealityScene scene;
	private final View sceneContainer;

	// ARCore turned out not to run, or not to measure depth, on this phone.
	private boolean unavailable;
	// The A.R. was showing before the activity was made again, or the editor was opened from the
	// A.R. view, see show; it starts when the activity resumes, once ARCore is known to support
	// the phone.
	private boolean startOnResume;
	// Whether ARCore supports this phone, as far as it knows before trying; null until it answers.
	private Boolean supported;
	private boolean resumed;
	// The editor was opened from the A.R. view, which takes ARCore's world back, see show.
	private boolean handsWorldBack;
	// The node was placed in the A.R. since the editor opened, so the user knows to tap.
	private boolean placed;
	// The last toast, which the next one replaces rather than queueing behind it.
	private Toast lastToast;
	// Whether the toggle's icon is turned to show the A.R. as on, see turnToggleIcon.
	private boolean toggleIconShown;
	// Where the scene last loaded the data around, NaN before it did.
	private double sceneLatitude = Double.NaN;
	private double sceneLongitude = Double.NaN;

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
		this.placeButton = container.findViewById(R.id.arPlaceButton);
		this.pin = container.findViewById(R.id.arPlacedPin);
		this.toggleButton = container.findViewById(R.id.arPlacementToggleButton);
		this.toggleIcon = new ViewSwitchDrawable(
				AppCompatResources.getDrawable(activity, R.drawable.ic_view_switch_frame),
				AppCompatResources.getDrawable(activity, R.drawable.ic_view_topo_ar),
				AppCompatResources.getDrawable(activity, R.drawable.ic_world_map));
		toggleButton.setImageDrawable(toggleIcon);
		this.sceneContainer = container.findViewById(R.id.arSceneContainer);
		this.scene = new AugmentedRealityScene(activity, sceneCamera, (ViewGroup) sceneContainer,
				this::updateScene);

		arCore = new ArCoreSession(activity, container, true, true, new ArCoreSession.Host() {
			@Override
			public void onStarted() {
				// The A.R. settings may have changed in the A.R. view.
				scene.loadSettings();
				scene.clearPins();
			}

			@Override
			public void onFailed(ArCoreSession.Failure failure) {
				switch (failure) {
					case NO_CAMERA:
						toast(R.string.ar_placement_no_camera);
						break;
					case INSTALL_DECLINED:
						toast(R.string.ar_placement_install_declined);
						break;
					case UNSUPPORTED:
						takeAwayButton(R.string.ar_placement_unsupported);
						break;
					case NO_DEPTH:
						takeAwayButton(R.string.ar_placement_no_depth);
						break;
					case CAMERA_UNAVAILABLE:
						toast(R.string.ar_placement_camera_unavailable);
						break;
					default:
						break;
				}
			}

			@Override
			public void onCameraMoved() {
				updateScene();
			}

			@Override
			public void onStateChanged() {
				updateLayers();
			}

			@Override
			public int idleHint() {
				return placed ? 0 : R.string.ar_placement_tap_to_place;
			}

			@Override
			public void onPinMoved() {
				if (pinMovePosted.compareAndSet(false, true)) {
					activity.runOnUiThread(movePin);
				}
			}

			@Override
			public void onTapped(CursorHit hit) {
				place(hit);
			}
		});

		GestureDetector taps = arCore.tapDetector(activity);
		// The height of the routes aligned on, as the scene stands their pins.
		arCore.setPinElevations(scene::getPinElevation);
		// For the line from where the A.R. has the route being aligned on to the cursor.
		arCore.setSeeThroughPin(scene::getSeeThroughPin);
		container.findViewById(R.id.arSurfaceView).setOnTouchListener((view, event) -> {
			holdForm(view, event);
			return taps.onTouchEvent(event);
		});
		placeButton.setOnClickListener(view -> place(arCore.getCursor()));
		toggleButton.setOnClickListener(view -> toggle());
		toggleButton.setVisibility(View.GONE);
		arCore.checkSupported(answer -> {
			supported = answer;
			if (answer && !unavailable) {
				toggleButton.setVisibility(View.VISIBLE);
			}
			startIfWanted();
		});
		arCore.updateStatus();
	}

	/**
	 * As on the editor's map, the form does not scroll while a finger is on the view.
	 */
	private static void holdForm(View view, MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			view.getParent().requestDisallowInterceptTouchEvent(true);
		} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			view.getParent().requestDisallowInterceptTouchEvent(false);
		}
	}

	/**
	 * Shows the A.R. again when it was showing before the activity was made again.
	 */
	@Override
	public void restore(Bundle savedInstanceState) {
		startOnResume = savedInstanceState != null && savedInstanceState.getBoolean(STATE_SHOWN);
		if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_HANDS_WORLD_BACK)) {
			setHandsWorldBack();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(STATE_SHOWN, arCore.isShown() || startOnResume);
		outState.putBoolean(STATE_HANDS_WORLD_BACK, handsWorldBack);
	}

	/**
	 * Shows the A.R. once the activity resumes, where ARCore runs, as when the editor is opened
	 * from the A.R. view, whose world it takes over, and hands back as the editor closes.
	 */
	@Override
	public void show() {
		startOnResume = true;
		setHandsWorldBack();
	}

	private void setHandsWorldBack() {
		handsWorldBack = true;
		arCore.setHandsWorldBack(true);
	}

	@Override
	public void onResume() {
		resumed = true;
		arCore.onResume();
		startIfWanted();
	}

	/**
	 * Starts the A.R. that is to show, once the activity is resumed and ARCore is known to
	 * support the phone: where it does not, the toggle never shows, and the map stays.
	 */
	private void startIfWanted() {
		if (!startOnResume || !resumed || supported == null) {
			return;
		}
		startOnResume = false;
		if (supported) {
			arCore.start();
		}
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

	/**
	 * Draws the placed node's pin as the node is now: its type and grade.
	 */
	@Override
	public void setNode(DisplayableGeoNode node) {
		long editedNodeId = node.getGeoNode().osmID;
		// Not to align on.
		arCore.setLeftOutNode(editedNodeId);
		// The node shows as it is being edited, by its own pin, not as it was saved.
		scene.leaveOut(editedNodeId);
		AugmentedRealityViewManager.setIcon(pin,
				AugmentedRealityViewManager.createIcon(activity, node));
	}

	/**
	 * Takes the placed pin away, for a node moved some other way, whose place in the A.R. is not
	 * known.
	 */
	@Override
	public void clearPin() {
		arCore.setPin(null);
	}

	/**
	 * Starts over for another node, not placed yet, with ARCore's world still tied to the map and
	 * the node just saved among the routes around.
	 */
	@Override
	public void nextNode() {
		clearPin();
		placed = false;
		// Loaded again on the next camera move, with the saved node in.
		sceneLatitude = Double.NaN;
		sceneLongitude = Double.NaN;
		scene.dataChanged();
		arCore.updateStatus();
	}

	/**
	 * Takes a compass reading, against which way ARCore has the camera look.
	 */
	@Override
	public void onOrientation(OrientationManager.OrientationEvent event) {
		arCore.onOrientation(event);
	}

	/**
	 * Takes a GPS fix, against where ARCore has the phone.
	 */
	@Override
	public void onLocation(double latitude, double longitude, double altitudeMeters,
	                       double accuracyMeters) {
		arCore.onLocation(latitude, longitude, altitudeMeters, accuracyMeters);
	}

	private void toggle() {
		if (arCore.isShown()) {
			arCore.stop();
		} else {
			arCore.start();
		}
	}

	private void updateLayers() {
		boolean shown = arCore.isShown();
		mapLayer.setVisibility(shown ? View.GONE : View.VISIBLE);
		arLayer.setVisibility(shown ? View.VISIBLE : View.GONE);
		turnToggleIcon();
		toggleButton.setContentDescription(activity.getString(
				shown ? R.string.ar_placement_hide : R.string.ar_placement_show));
		boolean aligning = arCore.isAligning();
		// While the user aligns on a route, only the pins of the routes aligned on show, and the
		// route's own see-through, where the A.R. has it, besides the one riding on the cursor.
		scene.showOnly(arCore.getShownWhileAligning(), arCore.getSeeThroughNodeId());
		if (!arCore.isRunning() || aligning) {
			pin.setVisibility(View.GONE);
		}
		// Place goes while the user aligns, as the cursor is the route's then, see
		// ArCoreSession.align, and Align drops it.
		placeButton.setVisibility(aligning ? View.GONE : View.VISIBLE);
		ArCoreSession.setIconEnabled(placeButton, arCore.isAiming() && arCore.isReady());
	}

	/**
	 * Turns the toggle's icon half a turn, the way its arrows go, when the view switches, to bring
	 * the view to switch to into the top circle.
	 */
	private void turnToggleIcon() {
		boolean shown = arCore.isShown();
		if (shown == toggleIconShown) {
			return;
		}
		toggleIconShown = shown;
		toggleIcon.turnTo(shown, null);
	}

	/**
	 * Puts the node where a tap, or the crosshair, meets the rock, as the session's world is tied
	 * to the map.
	 *
	 * @param hit where the tap or the crosshair meets the rock, null where nothing is known of it
	 */
	private void place(CursorHit hit) {
		if (!arCore.isRunning()) {
			return;
		}
		if (hit == null) {
			toast(R.string.ar_placement_tap_nothing);
			return;
		}
		double[] latLon = arCore.toLatLon(hit.point);
		if (latLon == null) {
			toast(R.string.ar_placement_tap_not_ready);
			return;
		}
		placeListener.onPlace(latLon[0], latLon[1]);
		arCore.setPin(hit.point);
		placed = true;
		if (hit.source == CursorHit.Source.SIGHTING) {
			arCore.endTriangulation();
		}
		arCore.updateStatus();
	}

	/**
	 * Lays the routes, crags and areas out as ARCore's camera sees them, once its world is tied to
	 * the map.
	 */
	private void updateScene() {
		boolean posed = arCore.poseCamera(sceneCamera);
		// Kept laid out while hidden, as it is sized to the view.
		sceneContainer.setVisibility(posed ? View.VISIBLE : View.INVISIBLE);
		if (!posed) {
			return;
		}

		double latitude = sceneCamera.decimalLatitude;
		double longitude = sceneCamera.decimalLongitude;
		double metersPerDegree = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
		// NaN, so far, before the first load.
		double moved = Math.hypot((latitude - sceneLatitude) * metersPerDegree,
				(longitude - sceneLongitude) * metersPerDegree
						* Math.cos(Math.toRadians(latitude)));
		if (!(moved < SCENE_RELOAD_METERS)) {
			sceneLatitude = latitude;
			sceneLongitude = longitude;
			scene.refreshAround(new Vector4d(latitude, longitude, sceneCamera.elevationMeters, 0));
		}
		scene.updateBoundingBox();
		scene.rotateWithCamera();
		scene.draw(scene.getEyeElevation(false));
	}

	private void movePin() {
		pinMovePosted.set(false);
		ArSessionRenderer.PinOnScreen onScreen = arCore.getPinOnScreen();
		if (!arCore.isRunning() || arCore.isAligning() || onScreen == null
				|| pin.getDrawable() == null) {
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
		if (lastToast != null) {
			lastToast.cancel();
		}
		lastToast = Toast.makeText(activity, message, Toast.LENGTH_LONG);
		lastToast.show();
	}
}
