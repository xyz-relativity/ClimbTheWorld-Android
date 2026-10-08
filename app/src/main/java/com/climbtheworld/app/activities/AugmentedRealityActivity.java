package com.climbtheworld.app.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.augmentedreality.HorizonMode;
import com.climbtheworld.app.augmentedreality.TerrainWireframe;
import com.climbtheworld.app.augmentedreality.TerrainWireframeView;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.TerrainElevation;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

public class AugmentedRealityActivity extends AppCompatActivity
		implements ILocationListener, ConfigFragment.OnConfigChangeListener, IOrientationListener {

	private static final int LOCATION_UPDATE_INTERVAL = 250;
	private static final double POI_CACHE_EVICTION_MARGIN = 1.5;
	// The phone is held above the ground the terrain model gives.
	private static final double EYE_HEIGHT_METERS = 1.5;
	// Terrain detail only pays off close by, where an elevation error moves things the most.
	private static final double TERRAIN_DETAIL_DISTANCE_METERS = 1000;
	private final Map<Long, GeoNode> boundingBoxPOIs = new HashMap<>();
	//POIs around the virtualCamera.
	private final List<GeoNode> visible = new ArrayList<>();
	private final List<GeoNode> zOrderedDisplay = new ArrayList<>();
	private final ConcurrentHashMap<Long, DisplayableGeoNode> arPOIs = new ConcurrentHashMap<>();
	private final Semaphore updatingView = new Semaphore(1);
	private final View[] compassBazelCardinals = new View[4];
	private final List<AlertDialog> startupDialogs = new ArrayList<>();
	private final DataManagerNew offlineDataManager = new DataManagerNew();
	private TerrainElevation terrainElevation;
	private TerrainWireframe terrainWireframe;
	private PreviewView cameraView;
	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;
	private View horizon;
	private TerrainWireframeView terrainWireframeView;
	private HorizonMode horizonMode = HorizonMode.OFF;
	private FloatingActionButton horizonModeButton;
	private boolean useElevation;
	private Toast hudToast;
	private Vector2d horizonSize = new Vector2d(1, 3);
	private MapLibreMapWidget mapWidget;
	private AugmentedRealityViewManager arViewManager;
	private CountDownTimer gpsUpdateAnimationTimer;
	private double maxDistance;
	private long lastFrame;
	private Configs configs;
	private double maxViewAngle = computeMaxViewAngle();
	private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
	private Camera camera;
	private Preview cameraPreview;
	private View compassBazel;

	private static boolean isInBoundingBox(GeoNode poi, double centerLatitude,
	                                       double centerLongitude,
	                                       double deltaLatitude, double deltaLongitude) {
		return (poi.decimalLatitude > centerLatitude - deltaLatitude &&
				poi.decimalLatitude < centerLatitude + deltaLatitude)
				&& (poi.decimalLongitude > centerLongitude - deltaLongitude &&
				poi.decimalLongitude < centerLongitude + deltaLongitude);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_augmented_reality);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});

		findViewById(R.id.compassLayout).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				startActivity(new Intent(AugmentedRealityActivity.this,
						EnvironmentActivity.class));
			}
		});

		configs = Configs.instance(this);
		terrainElevation = new TerrainElevation(this);
		terrainWireframe = new TerrainWireframe(this::getGroundElevation);

		//others
		Globals.virtualCamera.screenRotation =
				Globals.orientationToAngle(getWindowManager().getDefaultDisplay().getRotation());

		//camera
		this.cameraView = findViewById(R.id.cameraTexture);
		// The angle of view depends on how much of the camera stream the view has room to show.
		cameraView.addOnLayoutChangeListener((view, left, top, right, bottom,
		                                      oldLeft, oldTop, oldRight, oldBottom) -> {
			if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
				updateViewAngles();
			}
		});

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

						cameraProviderFuture =
								ProcessCameraProvider.getInstance(AugmentedRealityActivity.this);
						cameraProviderFuture.addListener(new Runnable() {
							@Override
							public void run() {
								try {
									ProcessCameraProvider cameraProvider =
											cameraProviderFuture.get();
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
				})
				.go();

		this.arViewManager = new AugmentedRealityViewManager(findViewById(R.id.arViewContainer));
		this.mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer),
				savedInstanceState, MapLibreMapWidget.Profile.AR_MINIMAP);

		initHUD();

		//location
		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE_INTERVAL);

		//orientation
		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_GAME);

		maxDistance = configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);

		updateMapViewCone();
		mapWidget.setInitialZoomToFitRadius(maxDistance);
		updateFilterIcon();
		showWarning();
	}

	/**
	 * Roll can turn any direction towards the corners of the view, so the widest angle from the
	 * camera axis that can still be on screen is the one to the corners.
	 */
	private static double computeMaxViewAngle() {
		Vector2d angleOfView = Globals.virtualCamera.angleOfViewDeg;
		return Math.toDegrees(Math.atan(Math.hypot(
				Math.tan(Math.toRadians(angleOfView.x / 2)),
				Math.tan(Math.toRadians(angleOfView.y / 2)))));
	}

	private void updateViewAngles() {
		if (camera == null) {
			return;
		}
		Globals.virtualCamera.computeViewAngles(this, camera, cameraPreview, cameraView);
		maxViewAngle = computeMaxViewAngle();
		updateMapViewCone();
	}

	/**
	 * Draws what the camera covers on the map: the horizontal angle of view, which is the azimuth
	 * span the AR view maps across its width, up to the display distance limit. maxViewAngle is a
	 * wider bound because it reaches the corners of the view.
	 */
	private void updateMapViewCone() {
		mapWidget.setViewCone(Globals.virtualCamera.angleOfViewDeg.x, maxDistance);
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

		arViewManager.getContainer().post(new Runnable() {
			public void run() {
				arViewManager.postInit();

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
		if (configs.getBoolean(Configs.ConfigKey.showExperimentalAR)) {
			Drawable icon = AppCompatResources.getDrawable(this, android.R.drawable.ic_dialog_info)
					.mutate();
			icon.setTint(ContextCompat.getColor(this, android.R.color.holo_green_light));

			AlertDialog experimentalDialog = new AlertDialog.Builder(AugmentedRealityActivity.this)
					.setCancelable(true)
					.setIcon(icon)
					.setTitle(getResources().getString(R.string.experimental_view))
					.setMessage(Html.fromHtml(
							getResources().getString(R.string.experimental_view_message)))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setNeutralButton(R.string.dont_show_again,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									configs.setBoolean(Configs.ConfigKey.showExperimentalAR,
											false);
								}
							}).create();
			experimentalDialog.setIcon(icon);
			experimentalDialog.show();
			((TextView) experimentalDialog.findViewById(android.R.id.message)).setMovementMethod(
					LinkMovementMethod.getInstance());
			startupDialogs.add(experimentalDialog);
		}

		if (configs.getBoolean(Configs.ConfigKey.showARWarning)) {
			Drawable icon = AppCompatResources.getDrawable(this,
							android.R.drawable.ic_dialog_alert)
					.mutate();
			icon.setTint(ContextCompat.getColor(this, android.R.color.holo_orange_light));

			AlertDialog warningDialog = new AlertDialog.Builder(AugmentedRealityActivity.this)
					.setCancelable(true)
					.setIcon(icon)
					.setTitle(getResources().getString(R.string.ar_warning))
					.setMessage(
							Html.fromHtml(getResources().getString(R.string.ar_warning_message)))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setNeutralButton(R.string.dont_show_again,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									configs.setBoolean(Configs.ConfigKey.showARWarning, false);
								}
							}).create();
			warningDialog.setIcon(icon);
			warningDialog.show();
			((TextView) warningDialog.findViewById(android.R.id.message)).setMovementMethod(
					LinkMovementMethod.getInstance());
			startupDialogs.add(warningDialog);
		}
	}

	@Override
	public void onDestroy() {
		mapWidget.onDestroy();
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
		} else if (id == R.id.horizonModeButton) {
			// Each mode comes without elevation first, then with it.
			boolean elevation = !useElevation;
			HorizonMode mode = elevation ? horizonMode : horizonMode.next();
			configs.setArElevation(elevation);
			configs.setHorizonMode(mode);
			useElevation = elevation;
			applyHorizonMode(mode);
			showHudToast(getString(R.string.ar_horizon_mode_toast, getString(mode.labelId),
					getString(elevation ? R.string.ar_elevation_on : R.string.ar_elevation_off)));
			updateView(true);
		} else if (id == R.id.toolsButton) {
			intent = new Intent(AugmentedRealityActivity.this, ToolsActivity.class);
			startActivityForResult(intent, Constants.OPEN_TOOLS_ACTIVITY);
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

	private void refreshNearbyPois(final Vector4d center) {
		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<Boolean>() {
					@Override
					protected Boolean doWork() {
						return offlineDataManager.loadAround(getApplicationContext(), center,
								maxDistance, arPOIs);
					}

					@Override
					protected void thenDoUiRelatedWork(Boolean result) {
						if (result) {
							updateBoundingBox(Globals.virtualCamera.decimalLatitude,
									Globals.virtualCamera.decimalLongitude,
									Globals.virtualCamera.elevationMeters);
						}
					}
				});
	}

	@Override
	protected void onStart() {
		super.onStart();
		mapWidget.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);

		mapWidget.onResume();

		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);

		useElevation = configs.isArElevation();
		applyHorizonMode(configs.getHorizonMode());

		updatePosition(Globals.virtualCamera.decimalLatitude,
				Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters, 1);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		if (gpsUpdateAnimationTimer != null) {
			gpsUpdateAnimationTimer.cancel();
		}
		mapWidget.onPause();

		Globals.onPause(this);
		super.onPause();
	}

	@Override
	protected void onStop() {
		mapWidget.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapWidget.onLowMemory();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		mapWidget.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
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
		mapWidget.onOrientationChange(event.camera);
		updateView(false);
	}

	public void updatePosition(final double pDecLatitude, final double pDecLongitude,
	                           final double pMetersAltitude, final double accuracy) {
		final int animationInterval = 100;

		refreshNearbyPois(new Vector4d(pDecLatitude, pDecLongitude, pMetersAltitude, 0));

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
					updateBoundingBox(Globals.virtualCamera.decimalLatitude,
							Globals.virtualCamera.decimalLongitude,
							Globals.virtualCamera.elevationMeters);
				}
			}

			public void onFinish() {
				Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude,
						pMetersAltitude);
				updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
			}
		}.start();
	}

	private void updateBoundingBox(final double pDecLatitude, final double pDecLongitude,
	                               final double pMetersAltitude) {
		double deltaLatitude = Math.toDegrees(maxDistance / GeoUtils.EARTH_RADIUS_M);
		double deltaLongitude = Math.toDegrees(
				maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * GeoUtils.EARTH_RADIUS_M));

		for (Long poiID : arPOIs.keySet()) {
			DisplayableGeoNode displayable = arPOIs.get(poiID);
			if (displayable == null) {
				continue;
			}

			GeoNode poi = displayable.getGeoNode();
			if (isInBoundingBox(poi, pDecLatitude, pDecLongitude, deltaLatitude, deltaLongitude)) {
				boundingBoxPOIs.put(poiID, poi);
				continue;
			}

			if (boundingBoxPOIs.containsKey(poiID)) {
				arViewManager.removePOIFromView(poi);
				boundingBoxPOIs.remove(poiID);
			}

			// Cached POIs are kept a margin beyond the loaded box so walking along its edge does
			// not drop and re-query the same nodes; past that they are released so the cache stays
			// bounded by the view distance instead of by how far the session has travelled.
			if (!isInBoundingBox(poi, pDecLatitude, pDecLongitude,
					deltaLatitude * POI_CACHE_EVICTION_MARGIN,
					deltaLongitude * POI_CACHE_EVICTION_MARGIN)) {
				arPOIs.remove(poiID);
			}
		}

		updateView(false);
	}

	private void updateView(boolean forced) {
		if (!forced && (System.currentTimeMillis() - lastFrame < Constants.TIME_TO_FRAME_MS)) {
			return;
		}
		lastFrame = System.currentTimeMillis();

		setOrientation();

		// Without elevation, every POI is on level ground with the observer, see
		// getElevationAngle, and unless the terrain is drawn, none is looked up, so none is
		// downloaded.
		boolean useTerrain = false;
		double observerElevation = Double.NaN;
		if (useElevation || horizonMode == HorizonMode.TERRAIN) {
			double observerGround = terrainElevation.getElevation(
					Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude,
					TerrainElevation.DETAIL_ZOOM);
			// The GPS altitude stands in until the terrain under the observer is loaded.
			useTerrain = !Double.isNaN(observerGround);
			observerElevation = useTerrain
					? observerGround + EYE_HEIGHT_METERS : getGpsElevation();
		}
		updateHorizon(useTerrain, observerElevation);
		if (!useElevation) {
			useTerrain = false;
			observerElevation = Double.NaN;
		}

		if (updatingView.tryAcquire()) {
			try {
				visible.clear();
				//find elements in view and sort them by distance.

				for (GeoNode poi : boundingBoxPOIs.values()) {

					double distance = GeoUtils.calculateDistance(Globals.virtualCamera, poi);

					if (distance < maxDistance && NodeDisplayFilters.matchFilters(configs, poi)) {
						double deltaAzimuth =
								GeoUtils.calculateTheoreticalAzimuth(Globals.virtualCamera, poi);
						double difAngle =
								GeoUtils.diffAngle(deltaAzimuth, Globals.virtualCamera.degAzimuth);
						double elevationAngle = getElevationAngle(observerElevation,
								getElevation(poi, distance, useTerrain), distance);

						if (AugmentedRealityUtils.angleFromCameraAxis(difAngle, elevationAngle,
								-Globals.virtualCamera.degPitch) <= maxViewAngle) {
							poi.distanceMeters = distance;
							poi.deltaDegAzimuth = deltaAzimuth;
							poi.difDegAngle = difAngle;
							poi.elevationDegAngle = elevationAngle;
							visible.add(poi);
							continue;
						}
					}
					arViewManager.removePOIFromView(poi);
				}

				Collections.sort(visible);

				//keep the closest elements, then stack them farthest first so the closest, largest
				//ones end up on top.
				int maxDisplayed = configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit);
				int displayLimit = 0;
				zOrderedDisplay.clear();
				for (GeoNode poi : visible) {
					if (displayLimit < maxDisplayed) {
						displayLimit++;

						zOrderedDisplay.add(poi);
					} else {
						arViewManager.removePOIFromView(poi);
					}
				}

				Collections.reverse(zOrderedDisplay);

				for (int i = 0; i < zOrderedDisplay.size(); i++) {
					arViewManager.addOrUpdatePOIToView(this, zOrderedDisplay.get(i), i);
				}
			} finally {
				updatingView.release();
			}
		}
	}

	/**
	 * @return the GPS altitude above sea level of the observer, NaN when unknown
	 */
	private double getGpsElevation() {
		// An elevation of 0 means unknown, see GeoNode.updatePOILocation.
		return Globals.virtualCamera.elevationMeters != 0
				? Globals.virtualCamera.elevationMeters : Double.NaN;
	}

	/**
	 * Elevation of the POI from the same source as the observer elevation, so the offset between
	 * sources does not move the POI. OSM elevations mostly come from phone GPS altitudes, which
	 * can be tens of meters away from the terrain model: at a close crag that is enough to put
	 * the routes far below the horizon.
	 *
	 * @param useTerrain whether the observer elevation comes from the terrain model rather than
	 *                   from the GPS altitude
	 * @return the elevation above sea level of the POI, NaN when unknown
	 */
	private double getElevation(GeoNode poi, double distance, boolean useTerrain) {
		if (useTerrain) {
			return getGroundElevation(poi.decimalLatitude, poi.decimalLongitude, distance);
		}
		// An elevation of 0 means unknown, see GeoNode.updatePOILocation.
		return poi.elevationMeters != 0 ? poi.elevationMeters : Double.NaN;
	}

	/**
	 * Without both elevations, the POI is taken to be on level ground with the observer, so close
	 * POIs still sit below the horizon as on flat ground, and move little once the terrain loads.
	 *
	 * @return the angle above the horizontal at which the observer sees the POI, in degree
	 */
	private static double getElevationAngle(double observerElevation, double poiElevation,
	                                        double distance) {
		if (Double.isNaN(observerElevation) || Double.isNaN(poiElevation)) {
			return Math.toDegrees(Math.atan2(-EYE_HEIGHT_METERS, distance));
		}
		return GeoUtils.calculateElevationAngle(observerElevation, poiElevation, distance);
	}

	/**
	 * Ground elevation from the terrain model, the same way for the POIs and the terrain
	 * wireframe so the POIs sit on it.
	 *
	 * @return the elevation above sea level, NaN while unknown
	 */
	private double getGroundElevation(double latitude, double longitude, double distance) {
		return terrainElevation.getElevation(latitude, longitude,
				distance < TERRAIN_DETAIL_DISTANCE_METERS
						? TerrainElevation.DETAIL_ZOOM : TerrainElevation.BASE_ZOOM);
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
				useElevation ? horizonMode.poisElevationIconId : horizonMode.poisFlatIconId);
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
			terrainWireframe.update(Globals.virtualCamera.decimalLatitude,
					Globals.virtualCamera.decimalLongitude, SystemClock.elapsedRealtime());
			terrainWireframeView.show(terrainWireframe, eyeElevation,
					arViewManager.getViewSize());
		}
	}

	private void setOrientation() {
		// Both compass and map location are viewed in the mirror, so they need to be rotated in
		// the opposite direction.
		Vector4d pos = AugmentedRealityUtils.getXYPosition(0, 0, -Globals.virtualCamera.degPitch,
				-Globals.virtualCamera.degRoll, Globals.virtualCamera.screenRotation,
				horizonSize, Globals.virtualCamera.angleOfViewDeg,
				arViewManager.getViewSize(), arViewManager.getContainerSize());

		arViewManager.setRotation((float) pos.w);
		horizon.setY((float) pos.y);

		compassBazel.setRotation((float) -Globals.virtualCamera.degAzimuth);
		for (View view : compassBazelCardinals) {
			view.setRotation((float) Globals.virtualCamera.degAzimuth);
		}
	}

	@Override
	public void onConfigChange() {
		for (GeoNode poi : boundingBoxPOIs.values()) {
			arViewManager.removePOIFromView(poi);
		}

		maxDistance = configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);

		updateMapViewCone();
		updateFilterIcon();
		mapWidget.invalidateData();

		updateView(true);
	}

	private void updateFilterIcon() {
		LayerDrawable icon = (LayerDrawable) ResourcesCompat.getDrawable(this.getResources(),
				R.drawable.ic_filter_checkable, null);
		if (icon == null) {
			return;
		}
		// The layers share ConstantState with every other use of the same drawables (e.g.
		// ic_done);
		// mutate so the alpha change stays local to this FAB icon.
		icon.mutate();

		icon.findDrawableByLayerId(R.id.icon_notification)
				.setAlpha(NodeDisplayFilters.hasFilters(configs) ? 255 : 0);
		((FloatingActionButton) findViewById(R.id.filterButton)).setImageDrawable(icon);
	}
}
