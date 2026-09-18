package com.climbtheworld.app.map.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
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
import org.maplibre.android.gestures.MoveGestureDetector;
import org.maplibre.android.gestures.RotateGestureDetector;
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
	private static final float MANUAL_ROTATION_DEADBAND_DEGREES = 12f;

	private enum RotationMode {
		STATIC, AUTO, USER
	}

	private static MapCameraState savedCamera = new MapCameraState(
			new MapCoordinate(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude,
					Globals.virtualCamera.elevationMeters), DEFAULT_ZOOM_LEVEL);

	private final AppCompatActivity parent;
	private final Configs configs;
	private final MapView mapView;
	private final View loadingIndicator;
	private final DataManager dataManager = new DataManager();
	private final Map<Long, DisplayableGeoNode> visiblePois = new ConcurrentHashMap<>();
	private final Map<Marker, DisplayableGeoNode> poiMarkers = new HashMap<>();

	private MapLibreMap map;
	private Marker observerMarker;
	private Marker tapMarker;
	private UiRelatedTask<Boolean> updateTask;
	private MapCoordinate observerLocation;
	private MapCoordinate tapLocation;
	private boolean followObserver = true;
	private boolean suppressNextCameraRefresh;
	private boolean styleLoaded;
	private RotationMode rotationMode = RotationMode.STATIC;

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState) {
		this.parent = parent;
		this.configs = Configs.instance(parent);
		this.mapView = container.findViewById(R.id.openMapView);
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
		map.getUiSettings().setAttributionGravity(Gravity.BOTTOM | Gravity.START);
		int attributionMargin = Globals.convertDpToPixel(8).intValue();
		map.getUiSettings().setAttributionMargins(attributionMargin, attributionMargin, attributionMargin, attributionMargin);
		map.getGesturesManager().getRotateGestureDetector().setAngleThreshold(MANUAL_ROTATION_DEADBAND_DEGREES);
		map.addOnMapClickListener(point -> {
			tapLocation = fromLatLng(point);
			setFollowObserver(false);
			updateTapMarker();
			return true;
		});
		map.addOnMoveListener(new MapLibreMap.OnMoveListener() {
			@Override
			public void onMoveBegin(@NonNull MoveGestureDetector detector) {
				suppressNextCameraRefresh = false;
				setFollowObserver(false);
			}

			@Override
			public void onMove(@NonNull MoveGestureDetector detector) {
			}

			@Override
			public void onMoveEnd(@NonNull MoveGestureDetector detector) {
			}
		});
		map.addOnRotateListener(new MapLibreMap.OnRotateListener() {
			@Override
			public void onRotateBegin(@NonNull RotateGestureDetector detector) {
				if (rotationMode != RotationMode.USER) {
					setRotationMode(RotationMode.USER);
				}
			}

			@Override
			public void onRotate(@NonNull RotateGestureDetector detector) {
				updateCompassButton();
			}

			@Override
			public void onRotateEnd(@NonNull RotateGestureDetector detector) {
				updateCompassButton();
			}
		});
		map.addOnCameraIdleListener(() -> {
			if (!styleLoaded) {
				return;
			}
			saveCamera();
			if (suppressNextCameraRefresh) {
				suppressNextCameraRefresh = false;
				return;
			}
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
		loadSelectedStyle();
	}

	private void configureControls(View container) {
		container.findViewById(R.id.mapLayerToggleButton).setOnClickListener(view -> selectNextStyle());

		ImageView locationButton = container.findViewById(R.id.mapCenterOnGpsButton);
		if (locationButton != null) {
			locationButton.setOnClickListener(view -> setFollowObserver(true));
		}

		View zoomInButton = container.findViewById(R.id.mapZoomInButton);
		if (zoomInButton != null) {
			zoomInButton.setOnClickListener(view -> {
				if (map != null) {
					map.animateCamera(CameraUpdateFactory.zoomIn());
				}
			});
		}

		View zoomOutButton = container.findViewById(R.id.mapZoomOutButton);
		if (zoomOutButton != null) {
			zoomOutButton.setOnClickListener(view -> {
				if (map != null) {
					map.animateCamera(CameraUpdateFactory.zoomOut());
				}
			});
		}

		ImageView compassButton = container.findViewById(R.id.compassButton);
		if (compassButton != null) {
			compassButton.setOnClickListener(view -> {
				setRotationMode(RotationMode.values()[(rotationMode.ordinal() + 1) % RotationMode.values().length]);
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
		map.setStyle(style.getStyleUrl(), loadedStyle -> {
			styleLoaded = true;
			applyCamera(savedCamera, false);
			applyRotationMode();
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

	private void setRotationMode(RotationMode mode) {
		rotationMode = mode;
		configs.setInt(Configs.ConfigKey.mapViewCompassOrientation,
				parent.getClass().getSimpleName(), rotationMode.ordinal());
		applyRotationMode();
	}

	private void applyRotationMode() {
		if (map == null) {
			return;
		}

		map.getUiSettings().setRotateGesturesEnabled(true);
		if (rotationMode == RotationMode.STATIC) {
			rotateCamera(0);
		}
		updateCompassButton();
	}

	public void onLocationChange(MapCoordinate location) {
		observerLocation = location;
		updateObserverMarker();
		if (followObserver) {
			centerOnObserver();
		}
	}

	public void onOrientationChange(Vector4d orientation) {
		if (rotationMode == RotationMode.AUTO && map != null) {
			rotateCamera(-orientation.x);
			ImageView compassButton = parent.findViewById(R.id.compassButton);
			if (compassButton != null) {
				compassButton.setRotation(-(float) orientation.x);
			}
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
		updateTapMarker();
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
		observerMarker = null;
		tapMarker = null;
		addObserverMarker();
		addTapMarker();
		for (DisplayableGeoNode poi : visiblePois.values()) {
			addPoiMarker(poi);
		}
	}

	private void addObserverMarker() {
		observerMarker = map.addMarker(new MarkerOptions()
				.position(toLatLng(observerLocation))
				.icon(iconFromDrawable(R.drawable.ic_my_location)));
	}

	private void updateObserverMarker() {
		if (!styleLoaded || map == null) {
			return;
		}
		if (observerMarker == null) {
			addObserverMarker();
		} else {
			observerMarker.setPosition(toLatLng(observerLocation));
		}
	}

	private void addTapMarker() {
		tapMarker = map.addMarker(new MarkerOptions()
				.position(toLatLng(tapLocation))
				.icon(iconFromDrawable(R.drawable.ic_tap_marker)));
	}

	private void updateTapMarker() {
		if (!styleLoaded || map == null) {
			return;
		}
		if (tapMarker == null) {
			addTapMarker();
		} else {
			tapMarker.setPosition(toLatLng(tapLocation));
		}
	}

	private void addPoiMarker(DisplayableGeoNode poi) {
		poi.setGhost(!NodeDisplayFilters.matchFilters(configs, poi.geoNode));
		Drawable icon = new PoiMarkerDrawable(parent, null, poi, 0.5f, 1f, poi.getAlpha()).getDrawable();
		Marker marker = map.addMarker(new MarkerOptions()
				.position(new LatLng(poi.geoNode.decimalLatitude, poi.geoNode.decimalLongitude))
				.icon(bottomAnchoredIcon(icon)));
		poiMarkers.put(marker, poi);
	}

	private Icon iconFromDrawable(int drawableId) {
		Drawable drawable = ResourcesCompat.getDrawable(parent.getResources(), drawableId, null);
		return iconFromDrawable(drawable);
	}

	private Icon iconFromDrawable(Drawable drawable) {
		return iconFromDrawable(drawable, 255);
	}

	private Icon iconFromDrawable(Drawable drawable, int alpha) {
		Bitmap source = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas sourceCanvas = new Canvas(source);
		drawable.setBounds(0, 0, sourceCanvas.getWidth(), sourceCanvas.getHeight());
		drawable.draw(sourceCanvas);

		Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
		paint.setAlpha(alpha);
		new Canvas(result).drawBitmap(source, 0, 0, paint);
		return IconFactory.getInstance(parent).fromBitmap(result);
	}

	private Icon bottomAnchoredIcon(Drawable drawable) {
		Bitmap source = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
		Canvas sourceCanvas = new Canvas(source);
		drawable.setBounds(0, 0, sourceCanvas.getWidth(), sourceCanvas.getHeight());
		drawable.draw(sourceCanvas);

		Bitmap anchored = Bitmap.createBitmap(source.getWidth(), source.getHeight() * 2, Bitmap.Config.ARGB_8888);
		new Canvas(anchored).drawBitmap(source, 0, 0, null);
		return IconFactory.getInstance(parent).fromBitmap(anchored);
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
		suppressNextCameraRefresh = true;
		moveCamera(observerLocation, map.getCameraPosition().zoom, currentBearing(), true);
	}

	private double currentBearing() {
		return map == null ? 0 : map.getCameraPosition().bearing;
	}

	private void rotateCamera(double bearing) {
		if (map == null) {
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		suppressNextCameraRefresh = true;
		moveCamera(fromLatLng(camera.target), camera.zoom, bearing, false);
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
		if (map == null || !styleLoaded) {
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		savedCamera = new MapCameraState(fromLatLng(camera.target), camera.zoom);
	}

	private void updateLocationButton() {
		ImageView button = parent.findViewById(R.id.mapCenterOnGpsButton);
		if (button != null) {
			if (followObserver) {
				button.clearColorFilter();
			} else {
				button.setColorFilter(Color.parseColor("#aaffffff"));
			}
		}
	}

	private void updateCompassButton() {
		ImageView button = parent.findViewById(R.id.compassButton);
		if (button == null) {
			return;
		}
		int icon = rotationMode == RotationMode.USER ? R.drawable.ic_compass_user : R.drawable.ic_compass;
		button.setImageDrawable(ResourcesCompat.getDrawable(parent.getResources(), icon, null));
		if (rotationMode == RotationMode.STATIC) {
			button.setRotation(0);
		} else if (rotationMode == RotationMode.USER && map != null) {
			button.setRotation(-(float) map.getCameraPosition().bearing);
		}
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
