package com.climbtheworld.app.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.augmentedreality.ArCoreFeatures;
import com.climbtheworld.app.augmentedreality.ArViewTracking;
import com.climbtheworld.app.augmentedreality.AugmentedRealityScene;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.augmentedreality.CameraMinimap;
import com.climbtheworld.app.augmentedreality.HorizonMode;
import com.climbtheworld.app.augmentedreality.TerrainWireframe;
import com.climbtheworld.app.augmentedreality.TerrainWireframeView;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.sensors.pressure.Barometer;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.climbtheworld.app.utils.views.dialogs.SettingsDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class AugmentedRealityActivity extends MapWidgetActivity
		implements ConfigFragment.OnConfigChangeListener {

	private static final String TAG = AugmentedRealityActivity.class.getSimpleName();
	private static final int LOCATION_UPDATE_INTERVAL = 250;
	private final View[] compassBazelCardinals = new View[4];
	private final List<AlertDialog> startupDialogs = new ArrayList<>();
	// The pins, route lines and outlines, as the camera sees them.
	private AugmentedRealityScene scene;
	// ARCore's tracking, on the phones it runs on in the non-FOSS build.
	private ArViewTracking arCore;
	// Whether ARCore moves and turns Globals.virtualCamera, in place of GPS and the sensors, see
	// ArViewTracking.
	private boolean onArCore;
	// The minimap following ARCore's camera.
	private CameraMinimap cameraMinimap;
	private TerrainWireframe terrainWireframe;
	private PreviewView cameraView;
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;
	// Steadies the GPS altitude, where the phone has one.
	private Barometer barometer;
	private View horizon;
	private TerrainWireframeView terrainWireframeView;
	private HorizonMode horizonMode = HorizonMode.OFF;
	private FloatingActionButton horizonModeButton;
	private Toast hudToast;
	private Vector2d horizonSize = new Vector2d(1, 3);
	private CountDownTimer gpsUpdateAnimationTimer;
	private long lastFrame;
	private Configs configs;
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	private Camera camera;
	private Preview cameraPreview;
	private View compassBazel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_augmented_reality);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		findViewById(R.id.compassLayout).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				startActivity(new Intent(AugmentedRealityActivity.this,
						EnvironmentActivity.class));
			}
		});

		// As the map's new node button.
		((FloatingActionButton) findViewById(R.id.createButton)).setImageDrawable(
				MarkerUtils.getLayoutIcon(this, R.layout.icon_climbing_add_display));

		configs = Configs.instance(this);
		scene = new AugmentedRealityScene(this, Globals.virtualCamera,
				findViewById(R.id.arViewContainer), () -> {
					updateBoundingBox();
					updateNearestRoute();
				});
		terrainWireframe = new TerrainWireframe(scene::getGroundElevation);

		//others
		Globals.virtualCamera.screenRotation =
				Globals.orientationToAngle(getWindowManager().getDefaultDisplay().getRotation());
		// Until ARCore is aligned on routes again, see ArViewTracking.
		Globals.virtualCamera.alignedElevations = null;

		//camera
		this.cameraView = findViewById(R.id.cameraTexture);
		// The angle of view depends on how much of the camera stream the view has room to show.
		cameraView.addOnLayoutChangeListener((view, left, top, right, bottom,
		                                      oldLeft, oldTop, oldRight, oldBottom) -> {
			if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
				updateViewAngles();
			}
		});

		this.mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer),
				savedInstanceState, MapLibreMapWidget.Profile.AR_MINIMAP);
		cameraMinimap = new CameraMinimap(mapWidget);

		initHUD();

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE_INTERVAL);
		barometer = new Barometer(this);

		//orientation
		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_GAME);

		arCore = ArCoreFeatures.viewTracking(this, findViewById(R.id.arContainer),
				new ArViewTracking.Listener() {
					@Override
					public void onStarted() {
						// ARCore shows what the camera sees.
						cameraView.setVisibility(View.GONE);
					}

					@Override
					public void onGaveUp() {
						cameraView.setVisibility(View.VISIBLE);
						startCameraPreview();
					}

					@Override
					public void onMovesCameraChanged(boolean moves) {
						useArCore(moves);
					}

					@Override
					public void onCameraMoved() {
						cameraMinimap.show(Globals.virtualCamera, scene.getMaxDistance());
						updateBoundingBox();
					}

					@Override
					public void onAligningChanged(Set<Long> shownNodeIds, Long seeThroughNodeId) {
						// Only the routes aligned on show, and the one riding on the cursor
						// see-through, where the A.R. has it.
						scene.showOnly(shownNodeIds, seeThroughNodeId);
					}
				});
		// The height of the routes aligned on, as the scene stands their pins.
		arCore.setPinElevations(scene::getPinElevation);
		// For the line from where the A.R. has the route being aligned on to the cursor.
		arCore.setSeeThroughPin(scene::getSeeThroughPin);

		// Asked once what it starts is made.
		Ask.on(this)
				.id(501) // in case you are invoking multiple time Ask from same activity or
				// fragment
				.addPermission(Manifest.permission.CAMERA, R.string.ar_camera_rational)
				.addPermission(Manifest.permission.ACCESS_FINE_LOCATION,
						R.string.ar_location_rational)
				.onCompleteListener(new Ask.IOnCompleteListener() {
					@Override
					public void onCompleted(String[] granted, String[] denied) {
						if (denied.length > 0) {
							Toast.makeText(AugmentedRealityActivity.this,
									getText(R.string.no_camera_permissions),
									Toast.LENGTH_LONG).show();
							findViewById(R.id.cameraTextError).setVisibility(View.VISIBLE);
						}

						// Asked of Android itself: the answer lists only what was asked this
						// time.
						boolean camera = ContextCompat.checkSelfPermission(
								AugmentedRealityActivity.this, Manifest.permission.CAMERA)
								== PackageManager.PERMISSION_GRANTED;
						Log.i(TAG, "Camera " + (camera ? "allowed" : "not allowed")
								+ "; granted " + Arrays.toString(granted)
								+ ", denied " + Arrays.toString(denied));
						// ARCore takes the camera on the phones it runs on, and CameraX shows it
						// elsewhere.
						if (!camera || !arCore.start()) {
							startCameraPreview();
						}
					}
				})
				.go();

		updateMapViewCone();
		mapWidget.setInitialZoomToFitRadius(scene.getMaxDistance());
		updateFilterIcon();
		showWarning();
	}

	/**
	 * Shows the camera through CameraX, with the sensors' camera.
	 */
	private void startCameraPreview() {
		cameraProviderFuture = ProcessCameraProvider.getInstance(AugmentedRealityActivity.this);
		cameraProviderFuture.addListener(new Runnable() {
			@Override
			public void run() {
				try {
					ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
					cameraPreview = new Preview.Builder().build();
					camera = bindPreview(cameraProvider, cameraPreview);
					updateViewAngles();
				} catch (ExecutionException | InterruptedException e) {
					// No errors need to be handled for this Future.
					// This should never be reached.
				}
			}
		}, ContextCompat.getMainExecutor(AugmentedRealityActivity.this));
	}

	/**
	 * Lets ARCore move and turn Globals.virtualCamera, or gives that back to GPS and the sensors.
	 */
	private void useArCore(boolean on) {
		onArCore = on;
		orientationManager.setTurnsCamera(!on);
		if (on && gpsUpdateAnimationTimer != null) {
			gpsUpdateAnimationTimer.cancel();
		}
		if (!on) {
			// ARCore's pose follows the screen already, which the sensors' does not.
			Globals.virtualCamera.screenRotation =
					Globals.orientationToAngle(getWindowManager().getDefaultDisplay().getRotation());
			// Only ARCore follows the routes aligned on.
			Globals.virtualCamera.alignedElevations = null;
		}
	}

	private void updateViewAngles() {
		// Without CameraX, ARCore tells its own, see ArViewTracking.
		if (camera == null) {
			return;
		}
		Globals.virtualCamera.computeViewAngles(this, camera, cameraPreview, cameraView);
		updateMapViewCone();
	}

	/**
	 * Draws what the camera covers on the map: the horizontal angle of view, which is the azimuth
	 * span the AR view maps across its width, up to the display distance limit. The scene places
	 * the pins within a wider angle, which reaches the corners of the view.
	 */
	private void updateMapViewCone() {
		mapWidget.setViewCone(Globals.virtualCamera.angleOfViewDeg.x, scene.getMaxDistance());
	}

	private void initHUD() {
		this.horizon = findViewById(R.id.horizon);
		this.terrainWireframeView = findViewById(R.id.terrainWireframe);
		this.horizonModeButton = findViewById(R.id.horizonModeButton);
		this.compassBazel = findViewById(R.id.compassBazel);
		this.compassBazelCardinals[0] = findViewById(R.id.compassNorthLabel);
		this.compassBazelCardinals[1] = findViewById(R.id.compassEastLabel);
		this.compassBazelCardinals[2] = findViewById(R.id.compassSouthLabel);
		this.compassBazelCardinals[3] = findViewById(R.id.compassWestLabel);

		scene.getViewManager().getContainer().post(new Runnable() {
			public void run() {
				horizonSize = new Vector2d(horizon.getLayoutParams().width,
						horizon.getLayoutParams().height);
			}
		});
	}

	Camera bindPreview(@NonNull ProcessCameraProvider cameraProvider, @NonNull Preview preview) {
		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(CameraSelector.LENS_FACING_BACK)
				.build();

		preview.setSurfaceProvider(cameraView.getSurfaceProvider());

		cameraProvider.unbindAll();

		return cameraProvider.bindToLifecycle(this, cameraSelector, preview);
	}

	private void showWarning() {
		if (configs.getBoolean(Configs.ConfigKey.showARWarning)) {
			showStartupNotice(android.R.drawable.ic_dialog_alert, android.R.color.holo_orange_light,
					R.string.ar_warning, R.string.ar_warning_message,
					Configs.ConfigKey.showARWarning);
		}
	}

	/**
	 * Shows a notice about the view, with its links tappable, which "don't show again" turns off
	 * through its setting.
	 */
	private void showStartupNotice(@DrawableRes int iconId, @ColorRes int tintId,
	                               @StringRes int titleId, @StringRes int messageId,
	                               Configs.ConfigKey shownSetting) {
		Drawable icon = DialogBuilder.tintedIcon(this, iconId, tintId);

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setCancelable(true)
				.setIcon(icon)
				.setTitle(getResources().getString(titleId))
				.setMessage(Html.fromHtml(getResources().getString(messageId)))
				.setPositiveButton(android.R.string.ok, (shown, which) -> shown.dismiss())
				.setNeutralButton(R.string.dont_show_again,
						(shown, which) -> configs.setBoolean(shownSetting, false))
				.create();
		dialog.setIcon(icon);
		dialog.show();
		((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(
				LinkMovementMethod.getInstance());
		startupDialogs.add(dialog);
	}

	@Override
	public void onDestroy() {
		arCore.onDestroy();
		for (AlertDialog startupDialog : startupDialogs) {
			startupDialog.dismiss();
		}
		startupDialogs.clear();
		super.onDestroy();
	}

	public void onClick(View v) {
		Intent intent;
		int id = v.getId();
		if (id == R.id.filterButton) {
			FilterDialogue.showFilterDialog(this, this);
		} else if (id == R.id.arSettingsButton) {
			SettingsDialogue.showAugmentedReality(this, this::onArSettingsChange);
		} else if (id == R.id.horizonModeButton) {
			// Each mode comes without elevation first, then with it.
			boolean elevation = !scene.usesElevation();
			HorizonMode mode = elevation ? horizonMode : horizonMode.next();
			configs.setArElevation(elevation);
			configs.setHorizonMode(mode);
			scene.loadSettings();
			applyHorizonMode(mode);
			showHudToast(getString(R.string.ar_horizon_mode_toast, getString(mode.labelId),
					getString(elevation ? R.string.ar_elevation_on : R.string.ar_elevation_off)));
			updateView(true);
		} else if (id == R.id.toolsButton) {
			intent = new Intent(AugmentedRealityActivity.this, ToolsActivity.class);
			startActivityForResult(intent, Constants.OPEN_TOOLS_ACTIVITY);
		} else if (id == R.id.createButton) {
			// A new node where the camera is, placed in the editor's A.R., which takes ARCore's
			// world over from this view as the editor covers it, with the routes aligned on, so
			// that it need not be aligned again; see ArViewTracking.handOver. The view starts
			// over once the editor closes, see onActivityResult, and takes the world back.
			intent = new Intent(AugmentedRealityActivity.this, EditNodeActivity.class);
			intent.putExtra(EditNodeActivity.EXTRA_AR_PLACEMENT, true);
			arCore.handOver();
			startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
		}
	}

	private void showHudToast(CharSequence text) {
		// Tapping through the modes replaces the text shown rather than queueing them.
		if (hudToast != null) {
			hudToast.cancel();
		}
		hudToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
		hudToast.show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
		barometer.start();
		arCore.onResume();

		scene.loadSettings();
		applyHorizonMode(configs.getHorizonMode());

		moveTo(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude,
				Globals.virtualCamera.elevationMeters);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		barometer.stop();
		arCore.onPause();
		if (gpsUpdateAnimationTimer != null) {
			gpsUpdateAnimationTimer.cancel();
		}
		super.onPause();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constants.OPEN_EDIT_ACTIVITY ||
				requestCode == Constants.OPEN_TOOLS_ACTIVITY) {
			Intent intent = getIntent();
			finish();
			startActivity(intent);
		}
	}

	public void updateOrientation(OrientationManager.OrientationEvent event) {
		arCore.onOrientation(event);
		// ARCore turns the camera, see ArViewTracking.Listener.onCameraMoved.
		if (onArCore) {
			return;
		}
		mapWidget.onOrientationChange(event.camera, event);
		updateView(false);
	}

	public void updatePosition(final double pDecLatitude, final double pDecLongitude,
	                           final double pMetersAltitude, final double accuracy) {
		// GPS, steadied by the barometer.
		double altitude = barometer.anchor(pMetersAltitude);
		arCore.onLocation(pDecLatitude, pDecLongitude, altitude, accuracy);
		moveTo(pDecLatitude, pDecLongitude, altitude);
		updateNearestRoute();
	}

	/**
	 * Tells ARCore how far the closest mapped route is, as it shows its tools to align on them
	 * near them only.
	 */
	private void updateNearestRoute() {
		arCore.setNearestRouteMeters(scene.getNearestDistance(OsmEntity.EntityClimbingType.route));
	}

	/**
	 * Loads what is around a location, and moves the camera there, unless ARCore moves it.
	 */
	private void moveTo(final double pDecLatitude, final double pDecLongitude,
	                    final double pMetersAltitude) {
		final int animationInterval = 100;

		scene.refreshAround(new Vector4d(pDecLatitude, pDecLongitude, pMetersAltitude, 0));
		// ARCore moves the camera, see ArViewTracking.Listener.onCameraMoved.
		if (onArCore) {
			return;
		}

		if (gpsUpdateAnimationTimer != null) {
			gpsUpdateAnimationTimer.cancel();
		}

		//Do a nice animation when moving to a new GPS position.
		gpsUpdateAnimationTimer = new CountDownTimer(Math.min(LOCATION_UPDATE_INTERVAL,
				animationInterval * Constants.POS_UPDATE_ANIMATION_STEPS)
				, animationInterval) {
			public void onTick(long millisUntilFinished) {
				long numSteps = millisUntilFinished / animationInterval;
				if (numSteps != 0) {
					double xStepSize =
							(pDecLongitude - Globals.virtualCamera.decimalLongitude) / numSteps;
					double yStepSize =
							(pDecLatitude - Globals.virtualCamera.decimalLatitude) / numSteps;

					Globals.virtualCamera.updatePOILocation(
							Globals.virtualCamera.decimalLatitude + yStepSize,
							Globals.virtualCamera.decimalLongitude + xStepSize, pMetersAltitude);

					mapWidget.onLocationChange(new MapCoordinate(
							Globals.virtualCamera.decimalLatitude,
							Globals.virtualCamera.decimalLongitude,
							Globals.virtualCamera.elevationMeters));
					updateBoundingBox();
				}
			}

			public void onFinish() {
				Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude,
						pMetersAltitude);
				updateBoundingBox();
			}
		}.start();
	}

	private void updateBoundingBox() {
		scene.updateBoundingBox();
		// Forced, as a move must not be skipped: until the view is updated, the terrain
		// wireframe stays sampled around where the camera was, which can be meters away when
		// driving.
		updateView(true);
	}

	private void updateView(boolean forced) {
		if (!forced && (System.currentTimeMillis() - lastFrame < Constants.TIME_TO_FRAME_MS)) {
			return;
		}
		lastFrame = System.currentTimeMillis();

		setOrientation();

		// The terrain horizon looks the terrain up even when the routes do not follow it.
		AugmentedRealityScene.EyeElevation eye =
				scene.getEyeElevation(horizonMode == HorizonMode.TERRAIN);
		updateHorizon(eye.onTerrain(), eye.meters());
		scene.draw(eye);
	}

	/**
	 * The terrain wireframe only replaces the flat horizon once the terrain is known, see
	 * updateHorizon.
	 */
	private void applyHorizonMode(HorizonMode mode) {
		horizonMode = mode;
		updateHorizonModeIcon();
		horizon.setVisibility(mode != HorizonMode.OFF ? View.VISIBLE : View.INVISIBLE);
		terrainWireframeView.setVisibility(View.INVISIBLE);
	}

	/**
	 * The horizon mode, with a hint of how the POIs are placed on top.
	 */
	private void updateHorizonModeIcon() {
		Drawable modeIcon = AppCompatResources.getDrawable(this, horizonMode.iconId);
		Drawable poisIcon = AppCompatResources.getDrawable(this,
				scene.usesElevation() ? horizonMode.poisElevationIconId
						: horizonMode.poisFlatIconId);
		horizonModeButton.setImageDrawable(new LayerDrawable(new Drawable[]{modeIcon, poisIcon}));
	}

	/**
	 * In terrain mode, the terrain around the observer, as a wireframe, takes the place of the
	 * flat virtual horizon once the terrain under the observer is known.
	 */
	private void updateHorizon(boolean useTerrain, double eyeElevation) {
		boolean showTerrain = horizonMode == HorizonMode.TERRAIN && useTerrain;
		horizon.setVisibility(horizonMode != HorizonMode.OFF && !showTerrain
				? View.VISIBLE : View.INVISIBLE);
		terrainWireframeView.setVisibility(showTerrain ? View.VISIBLE : View.INVISIBLE);
		if (showTerrain) {
			double latitude = Globals.virtualCamera.decimalLatitude;
			double longitude = Globals.virtualCamera.decimalLongitude;
			terrainWireframe.update(latitude, longitude, SystemClock.elapsedRealtime());
			terrainWireframeView.show(terrainWireframe, latitude, longitude, eyeElevation,
					AugmentedRealityScene.EYE_HEIGHT_METERS, scene.getViewManager().getViewSize());
		}
	}

	private void setOrientation() {
		// Both compass and map location are viewed in the mirror, so they need to be rotated in
		// the opposite direction.
		Vector4d pos = AugmentedRealityUtils.getXYPosition(0, 0, -Globals.virtualCamera.degPitch,
				-Globals.virtualCamera.degRoll, Globals.virtualCamera.screenRotation,
				horizonSize, Globals.virtualCamera.angleOfViewDeg,
				scene.getViewManager().getViewSize(), scene.getViewManager().getContainerSize());

		scene.rotateWithCamera();
		horizon.setY((float) pos.y);

		compassBazel.setRotation((float) -Globals.virtualCamera.degAzimuth);
		for (View view : compassBazelCardinals) {
			view.setRotation((float) Globals.virtualCamera.degAzimuth);
		}
	}

	@Override
	public void onConfigChange() {
		scene.clearPins();
		scene.loadSettings();

		updateMapViewCone();
		updateFilterIcon();
		mapWidget.invalidateData();

		updateView(true);
	}

	/**
	 * The A.R. settings dialog also changes what onResume otherwise applies.
	 */
	private void onArSettingsChange() {
		scene.loadSettings();
		applyHorizonMode(configs.getHorizonMode());
		onConfigChange();
		// A longer view distance needs the POIs beyond the ones loaded so far.
		scene.refreshAround(new Vector4d(Globals.virtualCamera.decimalLatitude,
				Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters, 0));
	}

	private void updateFilterIcon() {
		NodeDisplayFilters.showFilterState(this, configs);
	}
}
