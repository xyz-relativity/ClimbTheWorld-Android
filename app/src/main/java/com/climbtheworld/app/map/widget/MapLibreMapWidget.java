package com.climbtheworld.app.map.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCameraState;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.style.MapStyleDefinition;
import com.climbtheworld.app.map.style.MapStyleRegistry;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import needle.UiRelatedTask;

/**
 * MapLibre implementation for the primary map screen.
 */
public class MapLibreMapWidget {
	private static final double DEFAULT_ZOOM_LEVEL = 16;
	private static final double CENTER_ON_LOCATION_ZOOM_LEVEL = 24;

	private enum RotationMode {
		STATIC, AUTO, USER
	}

	private static MapCameraState savedCamera = new MapCameraState(
			new MapCoordinate(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude,
					Globals.virtualCamera.elevationMeters), DEFAULT_ZOOM_LEVEL);

	private final AppCompatActivity parent;
	private final Configs configs;
	private final MapView mapView;
	private final TextView sourceName;
	private final View loadingIndicator;
	private final DataManager dataManager = new DataManager();
	private final Map<Long, DisplayableGeoNode> visiblePois = new ConcurrentHashMap<>();
	private final Map<Marker, DisplayableGeoNode> poiMarkers = new HashMap<>();

	private MapLibreMap map;
	private UiRelatedTask<Boolean> updateTask;
	private MapCoordinate observerLocation;
	private MapCoordinate tapLocation;
	private boolean followObserver = true;
	private boolean styleLoaded;
	private RotationMode rotationMode = RotationMode.STATIC;

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState) {
		this.parent = parent;
		this.configs = Configs.instance(parent);
		this.mapView = container.findViewById(R.id.openMapView);
		this.sourceName = container.findViewById(R.id.mapSourceName);
		this.loadingIndicator = container.findViewById(R.id.mapLoadingIndicator);
		this.observerLocation = new MapCoordinate(Globals.virtualCamera.decimalLatitude,
				Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
		this.tapLocation = observerLocation;

		mapView.onCreate(savedInstanceState);
		configureControls(container);
		mapView.getMapAsync(this::onMapReady);
	}

	private void onMapReady(MapLibreMap map) {
		this.map = map;
		map.getUiSettings().setCompassEnabled(false);
		map.getUiSettings().setLogoEnabled(false);
		map.addOnMapClickListener(point -> {
			tapLocation = fromLatLng(point);
			setFollowObserver(false);
			renderMarkers();
			return true;
		});
		map.addOnCameraIdleListener(() -> {
			saveCamera();
			invalidateData();
		});
		map.setOnMarkerClickListener(marker -> {
			DisplayableGeoNode poi = poiMarkers.get(marker);
			if (poi != null && poi.isShowPoiInfoDialog()) {
				poi.showOnClickDialog(parent);
				return true;
			}
			return false;
		});

		rotationMode = RotationMode.values()[configs.getInt(Configs.ConfigKey.mapViewCompassOrientation,
				parent.getClass().getSimpleName())];
		applyRotationMode();
		loadSelectedStyle();
	}

	private void configureControls(View container) {
		container.findViewById(R.id.mapLayerToggleButton).setOnClickListener(view -> selectNextStyle());

		ImageView locationButton = container.findViewById(R.id.mapCenterOnGpsButton);
		if (locationButton != null) {
			locationButton.setOnClickListener(view -> setFollowObserver(true));
		}

		ImageView compassButton = container.findViewById(R.id.compassButton);
		if (compassButton != null) {
			compassButton.setOnClickListener(view -> {
				rotationMode = RotationMode.values()[(rotationMode.ordinal() + 1) % RotationMode.values().length];
				configs.setInt(Configs.ConfigKey.mapViewCompassOrientation,
						parent.getClass().getSimpleName(), rotationMode.ordinal());
				applyRotationMode();
			});
		}

		updateLocationButton();
		updateCompassButton();
	}

	private void loadSelectedStyle() {
		if (map == null) {
			return;
		}

		MapStyleDefinition style = MapStyleRegistry.getStyle(configs.getString(Configs.ConfigKey.mapStyleId));
		sourceName.setText(style.getDisplayName());
		map.setStyle(style.getStyleUrl(), loadedStyle -> {
			styleLoaded = true;
			applyCamera(savedCamera, false);
			renderMarkers();
			invalidateData();
		});
	}

	private void selectNextStyle() {
		List<MapStyleDefinition> styles = MapStyleRegistry.getAvailableStyles();
		MapStyleDefinition selected = MapStyleRegistry.getStyle(configs.getString(Configs.ConfigKey.mapStyleId));
		int nextIndex = (styles.indexOf(selected) + 1) % styles.size();
		configs.setString(Configs.ConfigKey.mapStyleId, styles.get(nextIndex).getId());
		styleLoaded = false;
		loadSelectedStyle();
	}

	private void applyRotationMode() {
		if (map == null) {
			return;
		}

		map.getUiSettings().setRotateGesturesEnabled(rotationMode == RotationMode.USER);
		if (rotationMode == RotationMode.STATIC) {
			moveCamera(observerLocation, map.getCameraPosition().zoom, 0, false);
		}
		updateCompassButton();
	}

	public void onLocationChange(MapCoordinate location) {
		observerLocation = location;
		if (followObserver) {
			centerOnObserver();
		}
		renderMarkers();
	}

	public void onOrientationChange(Vector4d orientation) {
		if (rotationMode == RotationMode.AUTO && map != null) {
			moveCamera(observerLocation, map.getCameraPosition().zoom, -orientation.x, false);
		}
	}

	public void centerOnLocation(MapCoordinate location) {
		centerOnLocation(location, CENTER_ON_LOCATION_ZOOM_LEVEL);
	}

	public void centerOnLocation(MapCoordinate location, double zoom) {
		tapLocation = location;
		setFollowObserver(false);
		if (map == null) {
			savedCamera = new MapCameraState(location, zoom);
			return;
		}
		moveCamera(location, zoom, currentBearing(), true);
		renderMarkers();
	}

	public MapCoordinate getTapLocation() {
		return tapLocation;
	}

	public void invalidateData() {
		if (!styleLoaded || map == null) {
			return;
		}

		if (updateTask != null) {
			updateTask.cancel();
		}
		final MapBounds visibleBounds = getVisibleBounds();
		setLoading(true);
		updateTask = new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				visiblePois.clear();
				boolean loaded = dataManager.loadBBox(parent, visibleBounds, visiblePois);
				return loaded || visiblePois.isEmpty() || isCanceled();
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean completed) {
				if (completed && !isCanceled()) {
					renderMarkers();
				}
				setLoading(false);
			}
		};
		Constants.MAP_EXECUTOR.execute(updateTask);
	}

	private MapBounds getVisibleBounds() {
		LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
		return new MapBounds(bounds.getLatNorth(), bounds.getLonEast(), bounds.getLatSouth(), bounds.getLonWest());
	}

	private void renderMarkers() {
		if (!styleLoaded || map == null) {
			return;
		}

		map.clear();
		poiMarkers.clear();
		addObserverMarker();
		addTapMarker();
		for (DisplayableGeoNode poi : visiblePois.values()) {
			addPoiMarker(poi);
		}
	}

	private void addObserverMarker() {
		map.addMarker(new MarkerOptions()
				.position(toLatLng(observerLocation))
				.icon(iconFromDrawable(R.drawable.ic_my_location))
				.anchor(0.5f, 0.5f));
	}

	private void addTapMarker() {
		map.addMarker(new MarkerOptions()
				.position(toLatLng(tapLocation))
				.icon(iconFromDrawable(R.drawable.ic_tap_marker))
				.anchor(0.5f, 0.5f));
	}

	private void addPoiMarker(DisplayableGeoNode poi) {
		poi.setGhost(!NodeDisplayFilters.matchFilters(configs, poi.geoNode));
		Marker marker = map.addMarker(new MarkerOptions()
				.position(new LatLng(poi.geoNode.decimalLatitude, poi.geoNode.decimalLongitude))
				.icon(iconFromDrawable(MarkerUtils.getPoiIcon(parent, poi.geoNode,
						Globals.gradeToColorState(poi.geoNode.getLevelId(com.climbtheworld.app.storage.database.ClimbingTags.KEY_GRADE_TAG)))))
				.anchor(0.5f, 1f));
		marker.setAlpha(poi.getAlpha() / 255f);
		poiMarkers.put(marker, poi);
	}

	private Icon iconFromDrawable(int drawableId) {
		Drawable drawable = ResourcesCompat.getDrawable(parent.getResources(), drawableId, null);
		return iconFromDrawable(drawable);
	}

	private Icon iconFromDrawable(Drawable drawable) {
		Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);
		return IconFactory.getInstance(parent).fromBitmap(bitmap);
	}

	private void setFollowObserver(boolean enabled) {
		followObserver = enabled;
		if (enabled) {
			centerOnObserver();
		}
		updateLocationButton();
	}

	private void centerOnObserver() {
		if (map == null) {
			savedCamera = new MapCameraState(observerLocation, savedCamera.getZoom());
			return;
		}
		moveCamera(observerLocation, map.getCameraPosition().zoom, currentBearing(), true);
	}

	private double currentBearing() {
		return map == null ? 0 : map.getCameraPosition().bearing;
	}

	private void moveCamera(MapCoordinate target, double zoom, double bearing, boolean animate) {
		if (map == null) {
			return;
		}

		CameraPosition camera = new CameraPosition.Builder()
				.target(toLatLng(target))
				.zoom(zoom)
				.bearing(bearing)
				.build();
		if (animate) {
			map.animateCamera(CameraUpdateFactory.newCameraPosition(camera));
		} else {
			map.moveCamera(CameraUpdateFactory.newCameraPosition(camera));
		}
	}

	private void applyCamera(MapCameraState camera, boolean animate) {
		moveCamera(camera.getCenter(), camera.getZoom(), currentBearing(), animate);
	}

	private void saveCamera() {
		if (map == null) {
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		savedCamera = new MapCameraState(fromLatLng(camera.target), camera.zoom);
	}

	private void updateLocationButton() {
		ImageView button = parent.findViewById(R.id.mapCenterOnGpsButton);
		if (button != null) {
			button.setColorFilter(followObserver ? null : Color.parseColor("#aaffffff"));
		}
	}

	private void updateCompassButton() {
		ImageView button = parent.findViewById(R.id.compassButton);
		if (button == null) {
			return;
		}
		int icon = rotationMode == RotationMode.USER ? R.drawable.ic_compass_user : R.drawable.ic_compass;
		button.setImageDrawable(ResourcesCompat.getDrawable(parent.getResources(), icon, null));
	}

	private void setLoading(boolean visible) {
		if (loadingIndicator != null) {
			loadingIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}

	private static LatLng toLatLng(MapCoordinate coordinate) {
		return new LatLng(coordinate.getLatitude(), coordinate.getLongitude(), coordinate.getAltitudeMeters());
	}

	private static MapCoordinate fromLatLng(LatLng coordinate) {
		return new MapCoordinate(coordinate.getLatitude(), coordinate.getLongitude(), coordinate.getAltitude());
	}

	public void onStart() {
		mapView.onStart();
	}

	public void onResume() {
		mapView.onResume();
	}

	public void onPause() {
		saveCamera();
		mapView.onPause();
	}

	public void onStop() {
		mapView.onStop();
	}

	public void onLowMemory() {
		mapView.onLowMemory();
	}

	public void onDestroy() {
		mapView.onDestroy();
	}

	public void onSaveInstanceState(@NonNull Bundle outState) {
		mapView.onSaveInstanceState(outState);
	}
}
