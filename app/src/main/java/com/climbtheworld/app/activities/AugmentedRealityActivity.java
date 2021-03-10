package com.climbtheworld.app.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.ask.annotations.AskDenied;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.sensors.camera.AutoFitTextureView;
import com.climbtheworld.app.sensors.camera.CameraHandler;
import com.climbtheworld.app.sensors.camera.CameraTextureViewListener;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.views.dialogs.FilterDialogue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

public class AugmentedRealityActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, DisplayFilterFragment.OnFilterChangeListener {

	private AutoFitTextureView textureView;
	private CameraHandler camera;
	private CameraTextureViewListener cameraTextureListener;

	private OrientationManager orientationManager;
	private DeviceLocationManager deviceLocationManager;
	private View horizon;
	private Vector2d horizonSize = new Vector2d(1, 3);

	private Map<Long, GeoNode> boundingBoxPOIs = new HashMap<>(); //POIs around the virtualCamera.

	private MapViewWidget mapWidget;
	private AugmentedRealityViewManager arViewManager;
	private DataManager downloadManager;

	private CountDownTimer gpsUpdateAnimationTimer;
	private double maxDistance;

	private List<GeoNode> visible = new ArrayList<>();
	private List<GeoNode> zOrderedDisplay = new ArrayList<>();
	private ConcurrentHashMap<Long, DisplayableGeoNode> arPOIs = new ConcurrentHashMap<>();
	private Semaphore updatingView = new Semaphore(1);

	AlertDialog dialog;

	private long lastFrame;
	private static final int locationUpdate = 500;
	private Configs configs;
	private double cameraFOV;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_augmented_reality);

		configs = Configs.instance(this);

		//others
		Globals.virtualCamera.screenRotation = Globals.orientationToAngle(getWindowManager().getDefaultDisplay().getRotation());
		cameraFOV = Math.max(Globals.virtualCamera.fieldOfViewDeg.x / 2.0, Globals.virtualCamera.fieldOfViewDeg.y / 2.0);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.CAMERA
						, Manifest.permission.ACCESS_FINE_LOCATION)
				.withRationales(getString(R.string.ar_camera_rational),
						getString(R.string.ar_location_rational))
				.go();

		this.arViewManager = new AugmentedRealityViewManager(this, configs, R.id.arViewContainer);
		this.mapWidget = MapWidgetBuilder.getBuilder(this, true)
				.enableAutoDownload()
				.build();

		this.horizon = findViewById(R.id.horizon);

		arViewManager.getContainer().post(new Runnable() {
			public void run() {
				arViewManager.postInit();

				horizonSize = new Vector2d(horizon.getLayoutParams().width, horizon.getLayoutParams().height);
			}
		});

		this.downloadManager = new DataManager(this);

		//camera
		this.textureView = findViewById(R.id.cameraTexture);
		assert textureView != null;
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			camera = new CameraHandler((CameraManager) getSystemService(Context.CAMERA_SERVICE),
					AugmentedRealityActivity.this, this, textureView);
			cameraTextureListener = new CameraTextureViewListener(camera);
			textureView.setSurfaceTextureListener(cameraTextureListener);
		}

		//location
		deviceLocationManager = new DeviceLocationManager(this, locationUpdate, this);

		//orientation
		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_GAME);
		orientationManager.addListener(this);

		maxDistance = configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);

		updateFilterIcon();
		showWarning();
	}

	@AskDenied(Manifest.permission.CAMERA)
	public void cameraAccessDenied() {
		Toast.makeText(AugmentedRealityActivity.this, getText(R.string.no_camera_permissions),
				Toast.LENGTH_LONG).show();
		finish();
	}

	@AskDenied(Manifest.permission.ACCESS_FINE_LOCATION)
	public void locationAccessDenied() {
		Toast.makeText(AugmentedRealityActivity.this, getText(R.string.no_camera_permissions),
				Toast.LENGTH_LONG).show();
		finish();
	}

	private void showWarning() {
		super.onStart();
		if (configs.getBoolean(Configs.ConfigKey.showExperimentalAR)) {
			Drawable icon = getDrawable(android.R.drawable.ic_dialog_info).mutate();
			icon.setTint(getResources().getColor(android.R.color.holo_green_light));

			dialog = new AlertDialog.Builder(AugmentedRealityActivity.this)
					.setCancelable(true)
					.setIcon(icon)
					.setTitle(getResources().getString(R.string.experimental_view))
					.setMessage(Html.fromHtml(getResources().getString(R.string.experimental_view_message)))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							configs.setBoolean(Configs.ConfigKey.showExperimentalAR, false);
						}
					}).create();
			dialog.setIcon(icon);
			dialog.show();
			((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

		}

		if (configs.getBoolean(Configs.ConfigKey.showARWarning)) {
			Drawable icon = getDrawable(android.R.drawable.ic_dialog_alert).mutate();
			icon.setTint(getResources().getColor(android.R.color.holo_orange_light));

			dialog = new AlertDialog.Builder(AugmentedRealityActivity.this)
					.setCancelable(true)
					.setIcon(icon)
					.setTitle(getResources().getString(R.string.ar_warning))
					.setMessage(Html.fromHtml(getResources().getString(R.string.ar_warning_message)))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							configs.setBoolean(Configs.ConfigKey.showARWarning, false);
						}
					}).create();
			dialog.setIcon(icon);
			dialog.show();
			((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (dialog != null) {
			dialog.dismiss();
		}
	}

	public void onClick(View v) {
		Intent intent;
		switch (v.getId()) {
			case R.id.filterButton:
				FilterDialogue.showFilterDialog(this, this);
				break;

			case R.id.toolsButton:
				intent = new Intent(AugmentedRealityActivity.this, ToolsActivity.class);
				startActivityForResult(intent, Constants.OPEN_TOOLS_ACTIVITY);
				break;
		}
	}

	private void downloadAround(final Quaternion center) {
		Constants.AR_EXECUTOR
				.execute(new UiRelatedTask<Boolean>() {
					@Override
					protected Boolean doWork() {
						return downloadManager.loadAround(center, maxDistance, arPOIs);
					}

					@Override
					protected void thenDoUiRelatedWork(Boolean result) {
						if (result) {
							updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
						}
					}
				});
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);

		mapWidget.onResume();

		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			camera.startBackgroundThread();
			if (textureView.isAvailable()) {
				camera.openCamera(textureView.getWidth(), textureView.getHeight());
			} else {
				textureView.setSurfaceTextureListener(cameraTextureListener);
			}
		}

		deviceLocationManager.onResume();
		orientationManager.onResume();

		if (configs.getBoolean(Configs.ConfigKey.showVirtualHorizon)) {
			horizon.setVisibility(View.VISIBLE);
		} else {
			horizon.setVisibility(View.INVISIBLE);
		}

		updatePosition(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters, 1);
	}

	@Override
	protected void onPause() {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			camera.closeCamera();
			camera.stopBackgroundThread();
		}

		deviceLocationManager.onPause();
		orientationManager.onPause();
		mapWidget.onPause();

		Globals.onPause(this);
		super.onPause();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == Constants.OPEN_EDIT_ACTIVITY || requestCode == Constants.OPEN_TOOLS_ACTIVITY) {
			Intent intent = getIntent();
			finish();
			startActivity(intent);
		}
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		mapWidget.onOrientationChange(event);
		updateView(false);
	}

	public void updatePosition(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double accuracy) {
		final int animationInterval = 100;

		downloadAround(new Quaternion(pDecLatitude, pDecLongitude, pMetersAltitude, 0));

		if (gpsUpdateAnimationTimer != null) {
			gpsUpdateAnimationTimer.cancel();
		}

		//Do a nice animation when moving to a new GPS position.
		gpsUpdateAnimationTimer = new CountDownTimer(Math.min(locationUpdate, animationInterval * Constants.POS_UPDATE_ANIMATION_STEPS)
				, animationInterval) {
			public void onTick(long millisUntilFinished) {
				long numSteps = millisUntilFinished / animationInterval;
				if (numSteps != 0) {
					double xStepSize = (pDecLongitude - Globals.virtualCamera.decimalLongitude) / numSteps;
					double yStepSize = (pDecLatitude - Globals.virtualCamera.decimalLatitude) / numSteps;

					Globals.virtualCamera.updatePOILocation(Globals.virtualCamera.decimalLatitude + yStepSize,
							Globals.virtualCamera.decimalLongitude + xStepSize, pMetersAltitude);

					mapWidget.onLocationChange(Globals.poiToGeoPoint(Globals.virtualCamera));
					updateBoundingBox(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
				}
			}

			public void onFinish() {
				Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
				updateBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude);
			}
		}.start();
	}

	private void updateBoundingBox(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude) {
		double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
		double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

		for (Long poiID : arPOIs.keySet()) {
			GeoNode poi = arPOIs.get(poiID).getGeoNode();
			if ((poi.decimalLatitude > pDecLatitude - deltaLatitude && poi.decimalLatitude < pDecLatitude + deltaLatitude)
					&& (poi.decimalLongitude > pDecLongitude - deltaLongitude && poi.decimalLongitude < pDecLongitude + deltaLongitude)) {

				boundingBoxPOIs.put(poiID, poi);
			} else if (boundingBoxPOIs.containsKey(poiID)) {
				arViewManager.removePOIFromView(poi);
				boundingBoxPOIs.remove(poiID);
			}
		}

		updateView(false);
	}

	private void updateView(boolean forced) {
		if (!forced && (System.currentTimeMillis() - lastFrame < Constants.TIME_TO_FRAME_MS)) {
			return;
		}
		lastFrame = System.currentTimeMillis();

		if (updatingView.tryAcquire()) {
			setOrientation();

			visible.clear();
			//find elements in view and sort them by distance.

			for (GeoNode poi : boundingBoxPOIs.values()) {

				double distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);

				if (distance < maxDistance) {
					double deltaAzimuth = AugmentedRealityUtils.calculateTheoreticalAzimuth(Globals.virtualCamera, poi);
					double difAngle = AugmentedRealityUtils.diffAngle(deltaAzimuth, Globals.virtualCamera.degAzimuth);

					if (Math.abs(difAngle) <= cameraFOV) {
						poi.distanceMeters = distance;
						poi.deltaDegAzimuth = deltaAzimuth;
						poi.difDegAngle = difAngle;
						visible.add(poi);
						continue;
					}
				}
				arViewManager.removePOIFromView(poi);
			}

			Collections.sort(visible);

			//display elements form largest to smallest. This will allow smaller elements to be clickable.
			int displayLimit = 0;
			zOrderedDisplay.clear();
			for (GeoNode poi : visible) {
				if (displayLimit < configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)) {
					displayLimit++;

					zOrderedDisplay.add(poi);
				} else {
					arViewManager.removePOIFromView(poi);
				}
			}

			Collections.reverse(zOrderedDisplay);

			for (GeoNode zpoi : zOrderedDisplay) {
				arViewManager.addOrUpdatePOIToView(zpoi);
			}

			updatingView.release();
		}
	}

	private void setOrientation() {
		// Both compass and map location are viewed in the mirror, so they need to be rotated in the opposite direction.
		Quaternion pos = AugmentedRealityUtils.getXYPosition(0, -Globals.virtualCamera.degPitch,
				-Globals.virtualCamera.degRoll, getWindowManager().getDefaultDisplay().getRotation(),
				horizonSize, Globals.virtualCamera.fieldOfViewDeg, arViewManager.getContainerSize());

		arViewManager.setRotation((float) pos.w);
		horizon.setY((float) pos.y);
	}

	@Override
	public void onFilterChange() {
		for (GeoNode poi : boundingBoxPOIs.values()) {
			arViewManager.removePOIFromView(poi);
		}

		updateFilterIcon();
		mapWidget.setClearState(true);
		mapWidget.invalidateData();

		updateView(true);
	}

	private void updateFilterIcon() {
		if (NodeDisplayFilters.hasFilters(configs)) {
			LayerDrawable icon = (LayerDrawable) ResourcesCompat.getDrawable(this.getResources(), R.drawable.ic_filter_checkable, null);
			icon.findDrawableByLayerId(R.id.icon_notification).setAlpha(255);
			((FloatingActionButton) findViewById(R.id.filterButton)).setImageDrawable(icon);
		} else {
			LayerDrawable icon = (LayerDrawable) ResourcesCompat.getDrawable(this.getResources(), R.drawable.ic_filter_checkable, null);
			icon.findDrawableByLayerId(R.id.icon_notification).setAlpha(0);
			((FloatingActionButton) findViewById(R.id.filterButton)).setImageDrawable(icon);
		}
	}
}
