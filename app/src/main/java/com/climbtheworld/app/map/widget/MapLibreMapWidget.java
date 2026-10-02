package com.climbtheworld.app.map.widget;

import static org.maplibre.android.style.layers.PropertyFactory.fillColor;
import static org.maplibre.android.style.layers.PropertyFactory.fillOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap;
import static org.maplibre.android.style.layers.PropertyFactory.iconAnchor;
import static org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement;
import static org.maplibre.android.style.layers.PropertyFactory.iconImage;
import static org.maplibre.android.style.layers.PropertyFactory.iconRotate;
import static org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment;
import static org.maplibre.android.style.layers.PropertyFactory.lineCap;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineJoin;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;
import static org.maplibre.android.style.layers.PropertyFactory.symbolZOrder;
import static org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap;
import static org.maplibre.android.style.layers.PropertyFactory.textColor;
import static org.maplibre.android.style.layers.PropertyFactory.textField;
import static org.maplibre.android.style.layers.PropertyFactory.textFont;
import static org.maplibre.android.style.layers.PropertyFactory.textHaloColor;
import static org.maplibre.android.style.layers.PropertyFactory.textHaloWidth;
import static org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement;
import static org.maplibre.android.style.layers.PropertyFactory.textSize;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
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
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCameraState;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.map.style.MapStyleDefinition;
import com.climbtheworld.app.map.style.MapStyleRegistry;
import com.climbtheworld.app.map.widget.climbing.ClimbingGeometryBuilder;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.gestures.MoveGestureDetector;
import org.maplibre.android.gestures.RotateGestureDetector;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import needle.UiRelatedTask;

/**
 * MapLibre implementation for the primary map screen.
 */
public class MapLibreMapWidget {
	private static final double DEFAULT_ZOOM_LEVEL = 16;
	private static final long AMBIENT_CACHE_SIZE_BYTES = 300L * 1024L * 1024L;
	private static boolean ambientCacheConfigured;
	private static final double CENTER_ON_LOCATION_ZOOM_LEVEL = 24;
	private static final double POI_RENDER_MIN_ZOOM_LEVEL = MapZoomLevels.POI_AND_ROUTE_MIN;
	private static final int MARKER_RENDER_BATCH_SIZE = 4;
	private static final int MAX_CACHED_POI_ICONS = 200;
	private static final float HULL_OUTLINE_WIDTH_DP = 1f;
	private static final String HULL_FILL_SOURCE_ID = "ctw-hull-fill-source";
	private static final String HULL_OUTLINE_SOURCE_ID = "ctw-hull-outline-source";
	private static final String HULL_FILL_LAYER_ID = "ctw-hull-fill-layer";
	private static final String HULL_OUTLINE_LAYER_ID = "ctw-hull-outline-layer";
	private static final String HULL_FILL_COLOR_PROPERTY = "fillColor";
	private static final String WAY_SOURCE_ID = "ctw-way-source";
	private static final String WAY_LAYER_ID = "ctw-way-layer";
	private static final String VIEW_CONE_SOURCE_ID = "ctw-view-cone-source";
	private static final String VIEW_CONE_LAYER_ID = "ctw-view-cone-layer";
	private static final String VIEW_RANGE_SOURCE_ID = "ctw-view-range-source";
	private static final String VIEW_RANGE_LAYER_ID = "ctw-view-range-layer";
	private static final int VIEW_CONE_COLOR = 0xff2196f3;
	private static final float VIEW_CONE_FILL_OPACITY = 0.25f;
	private static final float VIEW_RANGE_LINE_OPACITY = 0.7f;
	private static final float VIEW_RANGE_LINE_WIDTH_DP = 1.5f;
	private static final int VIEW_CONE_ARC_SEGMENTS = 32;
	private static final int VIEW_RANGE_CIRCLE_SEGMENTS = 96;
	private static final double VIEW_CONE_HEADING_EPSILON_DEGREES = 1;
	private static final int FIT_RADIUS_PADDING_DP = 8;
	private static final String POI_SOURCE_ID = "ctw-poi-source";
	private static final String POI_LAYER_ID = "ctw-poi-layer";
	private static final String OBSERVER_SOURCE_ID = "ctw-observer-source";
	private static final String OBSERVER_LAYER_ID = "ctw-observer-layer";
	private static final String TAP_SOURCE_ID = "ctw-tap-source";
	private static final String TAP_LAYER_ID = "ctw-tap-layer";
	private static final String EDIT_SOURCE_ID = "ctw-edit-source";
	private static final String EDIT_LAYER_ID = "ctw-edit-layer";
	private static final String EDIT_IMAGE_ID = "ctw-edit-image";
	private static final String CLUSTER_SOURCE_ID = "ctw-cluster-source";
	private static final String CLUSTER_LAYER_ID = "ctw-cluster-layer";
	private static final String CLUSTER_IMAGE_ID = "ctw-cluster-image";
	private static final String CLUSTER_COUNT_PROPERTY = "point_count";
	private static final String CLUSTER_COUNT_LABEL_PROPERTY = "point_count_abbreviated";
	private static final float CLUSTER_TEXT_SIZE_SP = 14f;
	private static final String ICON_PROPERTY = "icon";
	private static final String POI_ID_PROPERTY = "poiId";
	private static final String ROTATION_PROPERTY = "rotation";
	private static final String OBSERVER_IMAGE_ID = "ctw-observer-image";
	private static final String TAP_IMAGE_ID = "ctw-tap-image";
	private static final String EMPTY_FEATURE_COLLECTION =
			"{\"type\":\"FeatureCollection\",\"features\":[]}";
	private static final float MANUAL_ROTATION_DEADBAND_DEGREES = 12f;
	private static MapCameraState savedCamera = new MapCameraState(
			new MapCoordinate(Globals.virtualCamera.decimalLatitude,
					Globals.virtualCamera.decimalLongitude,
					Globals.virtualCamera.elevationMeters), DEFAULT_ZOOM_LEVEL);
	private final AppCompatActivity parent;
	private final Configs configs;
	private final MapView mapView;
	private final View loadingIndicator;
	private final DataManagerNew mapLibreDataManager = new DataManagerNew();
	private final ClimbingGeometryBuilder climbingGeometryBuilder = new ClimbingGeometryBuilder();
	private final Map<Long, DisplayableGeoNode> visiblePois = new ConcurrentHashMap<>();
	private final Map<Long, DisplayableGeoNode> renderedPois = new HashMap<>();
	private final Map<String, ClimbingGeometryBuilder.GeometrySpec> renderedHullLabels =
			new HashMap<>();
	private final Map<Long, DisplayableGeoNode> pendingRenderedPois = new HashMap<>();
	private final Map<String, Bitmap> poiBitmaps = new HashMap<>();
	private final Set<String> registeredPoiImages = new HashSet<>();
	private final Set<String> registeredHullLabelImages = new HashSet<>();
	private final List<DisplayableGeoNode> pendingPoiMarkers = new ArrayList<>();
	private final List<String> pendingPoiFeatures = new ArrayList<>();
	private final boolean forceGhostPois;
	private final boolean showTapMarker;
	private List<ClimbingGeometryBuilder.GeometrySpec> pendingClimbingGeometry =
			Collections.emptyList();
	private String pendingHullFillGeoJson = EMPTY_FEATURE_COLLECTION;
	private String pendingHullOutlineGeoJson = EMPTY_FEATURE_COLLECTION;
	private List<String> pendingHullLabelFeatures = Collections.emptyList();
	private List<String> renderedHullLabelFeatures = Collections.emptyList();
	private String pendingWayGeoJson = EMPTY_FEATURE_COLLECTION;
	private String pendingClusterGeoJson = EMPTY_FEATURE_COLLECTION;
	private double pendingClimbingZoom;
	private MapLibreMap map;
	private UiRelatedTask<Boolean> updateTask;
	private int markerRenderGeneration;
	private int pendingPoiMarkerIndex;
	private MapCoordinate observerLocation;
	private MapCoordinate tapLocation;
	private double lastSensorHeadingDegrees;
	private float observerRotationDegrees;
	private double viewConeFieldOfViewDegrees;
	private double viewConeRangeMeters;
	private double initialFitRadiusMeters;
	private boolean initialFitPending;
	private boolean initialFitWaitingForLayout;
	private MapCoordinate renderedViewConeCenter;
	private double renderedViewConeHeadingDegrees = Double.NaN;
	private double renderedViewConeFieldOfViewDegrees;
	private double renderedViewConeRangeMeters;
	private DisplayableGeoNode editMarkerPoi;
	private OnMapClickListener onMapClickListener;
	private boolean followObserver = true;
	private boolean suppressNextCameraRefresh;
	private boolean refreshOnNextCameraIdle;
	private boolean styleLoaded;
	private RotationMode rotationMode = RotationMode.STATIC;

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState) {
		this(parent, container, savedInstanceState, false, true);
	}

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState,
	                         boolean forceGhostPois, boolean showTapMarker) {
		this.parent = parent;
		this.configs = Configs.instance(parent);
		this.forceGhostPois = forceGhostPois;
		this.showTapMarker = showTapMarker;
		this.mapView = container.findViewById(R.id.openMapView);
		this.loadingIndicator = container.findViewById(R.id.mapLoadingIndicator);
		this.observerLocation = new MapCoordinate(Globals.virtualCamera.decimalLatitude,
				Globals.virtualCamera.decimalLongitude, Globals.virtualCamera.elevationMeters);
		this.tapLocation = observerLocation;

		configureAmbientCache(parent);
		mapView.onCreate(savedInstanceState);
		configureControls(container);
		mapView.getMapAsync(this::onMapReady);
	}

	private static synchronized void configureAmbientCache(AppCompatActivity parent) {
		if (ambientCacheConfigured) {
			return;
		}
		OfflineManager.getInstance(parent.getApplicationContext())
				.setMaximumAmbientCacheSize(AMBIENT_CACHE_SIZE_BYTES, null);
		ambientCacheConfigured = true;
	}

	private static LatLng toLatLng(MapCoordinate coordinate) {
		return new LatLng(coordinate.getLatitude(), coordinate.getLongitude(),
				coordinate.getAltitudeMeters());
	}

	private static MapCoordinate fromLatLng(LatLng coordinate) {
		return new MapCoordinate(coordinate.getLatitude(), coordinate.getLongitude(),
				coordinate.getAltitude());
	}

	private void onMapReady(MapLibreMap map) {
		this.map = map;
		map.getUiSettings().setCompassEnabled(false);
		map.getUiSettings().setLogoEnabled(false);
		map.getUiSettings().setAttributionGravity(Gravity.BOTTOM | Gravity.START);
		int attributionMargin = Globals.convertDpToPixel(8).intValue();
		map.getUiSettings()
				.setAttributionMargins(attributionMargin, attributionMargin, attributionMargin,
						attributionMargin);
		map.getGesturesManager().getRotateGestureDetector()
				.setAngleThreshold(MANUAL_ROTATION_DEADBAND_DEGREES);
		map.addOnMapClickListener(point -> {
			PointF screenPoint = map.getProjection().toScreenLocation(point);
			if (zoomIntoCluster(screenPoint)) {
				return true;
			}
			List<Feature> features = map.queryRenderedFeatures(screenPoint, POI_LAYER_ID);
			if (!features.isEmpty() && features.get(0).hasProperty(POI_ID_PROPERTY)) {
				long poiId = features.get(0).getNumberProperty(POI_ID_PROPERTY).longValue();
				DisplayableGeoNode poi = renderedPois.get(poiId);
				if (poi != null && poi.isShowPoiInfoDialog()) {
					poi.showOnClickDialog(parent);
					return true;
				}
			}

			if (!features.isEmpty()
					&& features.get(0).hasProperty(ClimbingGeometryBuilder.LABEL_KEY_PROPERTY)) {
				String labelKey = features.get(0).getStringProperty(
						ClimbingGeometryBuilder.LABEL_KEY_PROPERTY);
				ClimbingGeometryBuilder.GeometrySpec geometry = renderedHullLabels.get(labelKey);
				if (geometry != null && geometry.collection != null
						&& geometry.labelCoordinate != null) {
					NodeDialogBuilder.showCollectionInfoDialog(parent, geometry.collection,
							geometry.labelCoordinate, geometry.relationElementCount);
					return true;
				}
			}

			if (onMapClickListener != null) {
				onMapClickListener.onMapClick(fromLatLng(point));
				setFollowObserver(false);
				return true;
			}

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
				updateObserverRotation();
			}

			@Override
			public void onRotateEnd(@NonNull RotateGestureDetector detector) {
				updateCompassButton();
				updateObserverRotation();
			}
		});
		map.addOnCameraIdleListener(() -> {
			if (!styleLoaded) {
				return;
			}
			saveCamera();
			if (refreshOnNextCameraIdle) {
				refreshOnNextCameraIdle = false;
				suppressNextCameraRefresh = false;
				invalidateData();
				return;
			}
			if (suppressNextCameraRefresh) {
				suppressNextCameraRefresh = false;
				return;
			}
			invalidateData();
		});
		rotationMode =
				RotationMode.values()[configs.getInt(Configs.ConfigKey.mapViewCompassOrientation,
						parent.getClass().getSimpleName())];
		loadSelectedStyle();
	}

	private void configureControls(View container) {
		container.findViewById(R.id.mapLayerToggleButton)
				.setOnClickListener(view -> selectNextStyle());

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
				setRotationMode(RotationMode.values()[(rotationMode.ordinal() + 1) %
						RotationMode.values().length]);
			});
		}

		updateLocationButton();
		updateCompassButton();
	}

	private void loadSelectedStyle() {
		if (map == null) {
			return;
		}

		MapStyleDefinition style =
				MapStyleRegistry.getStyle(configs.getString(Configs.ConfigKey.mapStyleId));
		map.setStyle(style.getStyleUrl(), loadedStyle -> {
			styleLoaded = true;
			registeredPoiImages.clear();
			registeredHullLabelImages.clear();
			renderedPois.clear();
			renderedHullLabels.clear();
			renderedHullLabelFeatures = Collections.emptyList();
			renderedViewConeCenter = null;
			renderedViewConeHeadingDegrees = Double.NaN;
			initializeOverlayLayers(loadedStyle);
			applyCamera(savedCamera, false);
			applyInitialFit();
			applyRotationMode();
			renderMarkers();
			invalidateData();
		});
	}


	private void initializeOverlayLayers(Style style) {
		style.addImage(OBSERVER_IMAGE_ID, bitmapFromDrawable(R.drawable.ic_my_location));
		style.addImage(TAP_IMAGE_ID, bitmapFromDrawable(R.drawable.ic_tap_marker));
		style.addImage(CLUSTER_IMAGE_ID, bitmapFromDrawable(MarkerUtils.getClusterIcon(parent,
				DisplayableGeoNode.CLUSTER_DEFAULT_COLOR, DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE)));

		style.addSource(new GeoJsonSource(HULL_FILL_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(HULL_OUTLINE_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(WAY_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(VIEW_CONE_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(VIEW_RANGE_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(POI_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(OBSERVER_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(TAP_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(EDIT_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
		style.addSource(new GeoJsonSource(CLUSTER_SOURCE_ID, EMPTY_FEATURE_COLLECTION,
				new GeoJsonOptions()
						.withCluster(true)
						.withClusterMaxZoom((int) Math.ceil(MapZoomLevels.AREA_MIN) - 1)
						.withClusterRadius(DisplayableGeoNode.CLUSTER_ICON_DP_SIZE)));

		style.addLayer(new FillLayer(HULL_FILL_LAYER_ID, HULL_FILL_SOURCE_ID)
				.withProperties(
						fillColor(Expression.toColor(Expression.get(HULL_FILL_COLOR_PROPERTY)))));
		style.addLayer(new LineLayer(HULL_OUTLINE_LAYER_ID, HULL_OUTLINE_SOURCE_ID)
				.withProperties(
						lineColor(0xff000000),
						lineWidth(HULL_OUTLINE_WIDTH_DP),
						lineJoin(Property.LINE_JOIN_ROUND),
						lineCap(Property.LINE_CAP_ROUND)));
		style.addLayer(new LineLayer(WAY_LAYER_ID, WAY_SOURCE_ID)
				.withProperties(
						lineColor(0xee3c3c3c),
						lineWidth(2f),
						lineJoin(Property.LINE_JOIN_ROUND),
						lineCap(Property.LINE_CAP_ROUND)));
		// Ground overlay for the AR field of view: the cone is drawn under the markers so the POIs
		// it covers stay readable.
		style.addLayer(new FillLayer(VIEW_CONE_LAYER_ID, VIEW_CONE_SOURCE_ID)
				.withProperties(
						fillColor(VIEW_CONE_COLOR),
						fillOpacity(VIEW_CONE_FILL_OPACITY)));
		style.addLayer(new LineLayer(VIEW_RANGE_LAYER_ID, VIEW_RANGE_SOURCE_ID)
				.withProperties(
						lineColor(VIEW_CONE_COLOR),
						lineOpacity(VIEW_RANGE_LINE_OPACITY),
						lineWidth(VIEW_RANGE_LINE_WIDTH_DP),
						lineJoin(Property.LINE_JOIN_ROUND),
						lineCap(Property.LINE_CAP_ROUND)));
		style.addLayer(new SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID)
				.withProperties(
						// POI pins and relation label pins share this layer so they depth-sort together.
						iconImage(Expression.coalesce(Expression.get(ICON_PROPERTY),
								Expression.get(ClimbingGeometryBuilder.LABEL_ICON_PROPERTY))),
						iconAnchor(Property.ICON_ANCHOR_BOTTOM),
						iconAllowOverlap(true),
						iconIgnorePlacement(true),
						// Pins lower on screen (closer to the viewer) draw over the ones behind them.
						symbolZOrder(Property.SYMBOL_Z_ORDER_VIEWPORT_Y)));
		// Below the area level every climbing POI is aggregated; single POIs show a count of one.
		SymbolLayer clusterLayer = new SymbolLayer(CLUSTER_LAYER_ID, CLUSTER_SOURCE_ID)
				.withProperties(
						iconImage(CLUSTER_IMAGE_ID),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						iconAllowOverlap(true),
						iconIgnorePlacement(true),
						textField(Expression.switchCase(
								Expression.has(CLUSTER_COUNT_PROPERTY),
								Expression.toString(Expression.get(CLUSTER_COUNT_LABEL_PROPERTY)),
								Expression.literal("1"))),
						textFont(new String[]{"Noto Sans Bold"}),
						textSize(CLUSTER_TEXT_SIZE_SP),
						textColor(Color.BLACK),
						textHaloColor(Color.WHITE),
						// The halo is measured in the style's own density independent pixels, so
						// the marker outline size applies here without a density conversion.
						textHaloWidth(DisplayableGeoNode.MARKER_TEXT_OUTLINE_DP),
						textAllowOverlap(true),
						textIgnorePlacement(true));
		clusterLayer.setMaxZoom(MapZoomLevels.AREA_MIN);
		style.addLayer(clusterLayer);
		style.addLayer(new SymbolLayer(OBSERVER_LAYER_ID, OBSERVER_SOURCE_ID)
				.withProperties(
						iconImage(OBSERVER_IMAGE_ID),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						iconRotate(Expression.get(ROTATION_PROPERTY)),
						iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		style.addLayer(new SymbolLayer(TAP_LAYER_ID, TAP_SOURCE_ID)
				.withProperties(
						iconImage(TAP_IMAGE_ID),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		style.addLayer(new SymbolLayer(EDIT_LAYER_ID, EDIT_SOURCE_ID)
				.withProperties(
						iconImage(EDIT_IMAGE_ID),
						iconAnchor(Property.ICON_ANCHOR_BOTTOM),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		if (editMarkerPoi != null) {
			renderEditMarker();
		}
		updateViewCone();
	}

	private void selectNextStyle() {
		List<MapStyleDefinition> styles = MapStyleRegistry.getAvailableStyles();
		MapStyleDefinition selected =
				MapStyleRegistry.getStyle(configs.getString(Configs.ConfigKey.mapStyleId));
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
		updateObserverRotation();
	}

	public void onLocationChange(MapCoordinate location) {
		observerLocation = location;
		updateObserverMarker();
		updateViewCone();
		if (followObserver) {
			centerOnObserver();
		}
	}

	/**
	 * Draws a translucent cone centred on the observer covering the given field of view and a
	 * circle outline at the given range. Both are geographic shapes, so they follow the map
	 * rotation. A field of view or range of zero hides them.
	 */
	public void setViewCone(double fieldOfViewDegrees, double rangeMeters) {
		viewConeFieldOfViewDegrees = fieldOfViewDegrees;
		viewConeRangeMeters = rangeMeters;
		updateViewCone();
	}

	/**
	 * Opens the map zoomed out far enough to show a circle of this radius around the observer.
	 * It is applied once, to the first camera this widget sets up, so panning and zooming
	 * afterwards is left alone.
	 */
	public void setInitialZoomToFitRadius(double radiusMeters) {
		initialFitRadiusMeters = radiusMeters;
		initialFitPending = radiusMeters > 0;
		applyInitialFit();
	}

	private void applyInitialFit() {
		if (!initialFitPending || !styleLoaded || map == null) {
			return;
		}

		if (mapView.getWidth() == 0 || mapView.getHeight() == 0) {
			// The style can finish loading before the first layout pass, and the fit needs the
			// view size. One retry is enough: the post runs once the pending pass is done.
			if (!initialFitWaitingForLayout) {
				initialFitWaitingForLayout = true;
				mapView.post(this::applyInitialFit);
			}
			return;
		}

		Vector2d offset = GeoUtils.latLongOffset(observerLocation.getLatitude(),
				new Vector2d(initialFitRadiusMeters, initialFitRadiusMeters));
		LatLngBounds bounds = new LatLngBounds(
				observerLocation.getLatitude() + offset.y,
				observerLocation.getLongitude() + offset.x,
				observerLocation.getLatitude() - offset.y,
				observerLocation.getLongitude() - offset.x);
		int padding = Globals.convertDpToPixel(FIT_RADIUS_PADDING_DP).intValue();
		CameraPosition fitted = map.getCameraForLatLngBounds(bounds,
				new int[]{padding, padding, padding, padding});
		if (fitted == null) {
			return;
		}

		initialFitPending = false;
		moveCamera(observerLocation, fitted.zoom, currentBearing(), false);
	}

	public void onOrientationChange(Vector4d orientation) {
		lastSensorHeadingDegrees = orientation.x;
		if (rotationMode == RotationMode.AUTO && map != null) {
			rotateCamera(orientation.x);
			ImageView compassButton = parent.findViewById(R.id.compassButton);
			if (compassButton != null) {
				compassButton.setRotation(-(float) orientation.x);
			}
		}
		updateObserverRotation();
		updateViewCone();
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
		if (showTapMarker) {
			updateTapMarker();
		}
	}

	/**
	 * Centers on the bounds and zooms to fit them, with the zoom clamped to [minZoom, maxZoom]
	 * (e.g. so a relation's label stays visible).
	 */
	public void centerOnBounds(MapBounds bounds, double minZoom, double maxZoom) {
		MapCoordinate center = new MapCoordinate((bounds.getNorth() + bounds.getSouth()) / 2,
				(bounds.getEast() + bounds.getWest()) / 2);
		double zoom = maxZoom;
		if (map != null && mapView.getWidth() > 0 && mapView.getHeight() > 0) {
			int padding = Globals.convertDpToPixel(FIT_RADIUS_PADDING_DP).intValue();
			CameraPosition fitted = map.getCameraForLatLngBounds(new LatLngBounds(
							bounds.getNorth(), bounds.getEast(), bounds.getSouth(), bounds.getWest()),
					new int[]{padding, padding, padding, padding});
			if (fitted != null) {
				zoom = fitted.zoom;
				if (fitted.target != null) {
					center = fromLatLng(fitted.target.wrap());
				}
			}
		}
		centerOnLocation(center, Math.max(minZoom, Math.min(maxZoom, zoom)));
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
		final double visibleZoom = map.getCameraPosition().zoom;
		setLoading(true);
		updateTask = new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				visiblePois.clear();
				boolean loaded = mapLibreDataManager.loadDisplayableNodesBBox(
						parent, visibleBounds, visiblePois);
				if (!isCanceled()) {
					pendingClimbingZoom = visibleZoom;
					pendingClimbingGeometry = climbingGeometryBuilder.load(parent, visibleBounds);
					pendingHullFillGeoJson = climbingGeometryBuilder.buildHullGeoJson(
							pendingClimbingGeometry, visibleZoom, false);
					pendingHullOutlineGeoJson = climbingGeometryBuilder.buildHullGeoJson(
							pendingClimbingGeometry, visibleZoom, true);
					pendingHullLabelFeatures = climbingGeometryBuilder.buildHullLabelFeatures(
							pendingClimbingGeometry, visibleZoom);
					pendingWayGeoJson = climbingGeometryBuilder.buildWayGeoJson(
							pendingClimbingGeometry, visibleZoom);
					pendingClusterGeoJson = buildClusterGeoJson(visibleZoom);
				}
				return loaded || visiblePois.isEmpty() || isCanceled();
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean completed) {
				if (completed && !isCanceled()) {
					renderClimbingGeometry();
					renderClusters();
					renderMarkers();
				}
				setLoading(false);
			}
		};
		Constants.MAP_EXECUTOR.execute(updateTask);
	}

	private MapBounds getVisibleBounds() {
		LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
		return new MapBounds(bounds.getLatNorth(), bounds.getLonEast(), bounds.getLatSouth(),
				bounds.getLonWest());
	}

	private String buildClusterGeoJson(double zoom) {
		if (zoom >= MapZoomLevels.AREA_MIN) {
			return EMPTY_FEATURE_COLLECTION;
		}
		List<String> features = new ArrayList<>();
		for (DisplayableGeoNode poi : visiblePois.values()) {
			if (!forceGhostPois && !NodeDisplayFilters.matchFilters(configs, poi.geoNode)) {
				continue;
			}
			features.add("{\"type\":\"Feature\",\"properties\":{},"
					+ "\"geometry\":{\"type\":\"Point\",\"coordinates\":["
					+ poi.geoNode.decimalLongitude + "," + poi.geoNode.decimalLatitude + "]}}");
		}
		return featureCollection(features);
	}

	private void renderClusters() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		GeoJsonSource source = map.getStyle().getSourceAs(CLUSTER_SOURCE_ID);
		if (source != null) {
			source.setGeoJson(pendingClusterGeoJson);
		}
	}

	private boolean zoomIntoCluster(PointF screenPoint) {
		if (map.getCameraPosition().zoom >= MapZoomLevels.AREA_MIN || map.getStyle() == null) {
			return false;
		}
		List<Feature> clusters = map.queryRenderedFeatures(screenPoint, CLUSTER_LAYER_ID);
		if (clusters.isEmpty() || !(clusters.get(0).geometry() instanceof Point)) {
			return false;
		}
		Feature cluster = clusters.get(0);
		Point center = (Point) cluster.geometry();
		double targetZoom = MapZoomLevels.AREA_MIN;
		GeoJsonSource source = map.getStyle().getSourceAs(CLUSTER_SOURCE_ID);
		if (source != null && cluster.hasProperty(CLUSTER_COUNT_PROPERTY)) {
			targetZoom = Math.min(source.getClusterExpansionZoom(cluster), targetZoom);
		}
		setFollowObserver(false);
		moveCamera(new MapCoordinate(center.latitude(), center.longitude()), targetZoom,
				currentBearing(), true);
		return true;
	}

	private void renderClimbingGeometry() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}

		Style style = map.getStyle();
		registerHullLabelImages(style);
		renderedHullLabels.clear();
		for (ClimbingGeometryBuilder.GeometrySpec geometry : pendingClimbingGeometry) {
			if (geometry.isLabelVisibleAt(pendingClimbingZoom) && geometry.collection != null) {
				renderedHullLabels.put(geometry.key, geometry);
			}
		}
		GeoJsonSource fillSource = style.getSourceAs(HULL_FILL_SOURCE_ID);
		GeoJsonSource outlineSource = style.getSourceAs(HULL_OUTLINE_SOURCE_ID);
		GeoJsonSource waySource = style.getSourceAs(WAY_SOURCE_ID);
		if (fillSource != null) {
			fillSource.setGeoJson(pendingHullFillGeoJson);
		}
		if (outlineSource != null) {
			outlineSource.setGeoJson(pendingHullOutlineGeoJson);
		}
		renderedHullLabelFeatures = pendingHullLabelFeatures;
		if (waySource != null) {
			waySource.setGeoJson(pendingWayGeoJson);
		}
	}

	private void registerHullLabelImages(Style style) {
		for (ClimbingGeometryBuilder.GeometrySpec geometry : pendingClimbingGeometry) {
			if (!geometry.isLabelVisibleAt(pendingClimbingZoom)) {
				continue;
			}
			String imageId = geometry.getLabelImageId();
			if (registeredHullLabelImages.add(imageId)) {
				style.addImage(imageId, createRelationLabelBitmap(geometry));
			}
		}
	}

	private Bitmap createRelationLabelBitmap(
			ClimbingGeometryBuilder.GeometrySpec geometry) {
		try {
			JSONObject tags = new JSONObject(geometry.collection.getTags().toString());
			tags.put(ClimbingTags.KEY_ROUTES,
					Integer.toString(geometry.relationElementCount));
			GeoNode relation = new GeoNode(
					new JSONObject(geometry.collection.jsonNodeInfo.toString()));
			relation.setTags(tags);
			relation.updatePOILocation(geometry.labelCoordinate.getLatitude(),
					geometry.labelCoordinate.getLongitude(),
					geometry.labelCoordinate.getAltitudeMeters());
			Drawable drawable = new PoiMarkerDrawable(
					parent, new DisplayableGeoNode(relation)).getDrawable();
			return bitmapFromDrawable(drawable);
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to render relation POI label", exception);
		}
	}

	private void renderMarkers() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}

		markerRenderGeneration++;
		pendingPoiMarkers.clear();
		pendingPoiMarkers.addAll(getVisiblePoisToRender());
		pendingPoiMarkerIndex = 0;
		pendingPoiFeatures.clear();
		pendingRenderedPois.clear();
		updateObserverMarker();
		if (showTapMarker) {
			updateTapMarker();
		}
		renderNextPoiMarkerBatch(markerRenderGeneration);
	}

	private List<DisplayableGeoNode> getVisiblePoisToRender() {
		double zoom = map.getCameraPosition().zoom;
		List<DisplayableGeoNode> pois = new ArrayList<>();
		for (DisplayableGeoNode poi : visiblePois.values()) {
			if (isPoiVisibleAtZoom(poi.geoNode, zoom)) {
				pois.add(poi);
			}
		}
		Collections.sort(pois, Comparator.comparingLong(poi -> poi.geoNode.osmID));

		int limit = configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit);
		if (limit < pois.size()) {
			return new ArrayList<>(pois.subList(0, Math.max(limit, 0)));
		}
		return pois;
	}

	static boolean isPoiVisibleAtZoom(GeoNode poi, double zoom) {
		return zoom >= POI_RENDER_MIN_ZOOM_LEVEL
				|| (zoom >= MapZoomLevels.AREA_MIN
				&& poi.getNodeType() == GeoNode.NodeTypes.artificial);
	}

	private void renderNextPoiMarkerBatch(int generation) {
		if (generation != markerRenderGeneration || !styleLoaded || map == null ||
				map.getStyle() == null) {
			return;
		}

		int end = Math.min(pendingPoiMarkerIndex + MARKER_RENDER_BATCH_SIZE,
				pendingPoiMarkers.size());
		while (pendingPoiMarkerIndex < end) {
			preparePoiFeature(pendingPoiMarkers.get(pendingPoiMarkerIndex++), map.getStyle());
		}
		if (pendingPoiMarkerIndex < pendingPoiMarkers.size()) {
			mapView.post(() -> renderNextPoiMarkerBatch(generation));
		} else {
			publishPoiFeatures(generation);
		}
	}

	private void preparePoiFeature(DisplayableGeoNode poi, Style style) {
		poi.setGhost(forceGhostPois || !NodeDisplayFilters.matchFilters(configs, poi.geoNode));
		String iconKey = getPoiIconKey(poi);
		String imageId =
				"ctw-poi-" + poi.geoNode.osmID + "-" + Integer.toUnsignedString(iconKey.hashCode());
		Bitmap bitmap = poiBitmaps.get(iconKey);
		if (bitmap == null) {
			if (poiBitmaps.size() >= MAX_CACHED_POI_ICONS) {
				poiBitmaps.clear();
			}
			Drawable drawable = new PoiMarkerDrawable(parent, poi, poi.getAlpha()).getDrawable();
			bitmap = bitmapFromDrawable(drawable);
			poiBitmaps.put(iconKey, bitmap);
		}
		if (registeredPoiImages.add(imageId)) {
			style.addImage(imageId, bitmap);
		}

		long markerId = markerId(poi.geoNode);
		pendingRenderedPois.put(markerId, poi);
		pendingPoiFeatures.add("{\"type\":\"Feature\",\"properties\":{\""
				+ POI_ID_PROPERTY + "\":" + markerId + ",\"" + ICON_PROPERTY
				+ "\":\"" + imageId + "\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
				+ poi.geoNode.decimalLongitude + "," + poi.geoNode.decimalLatitude + "]}}");
	}

	private void publishPoiFeatures(int generation) {
		if (generation != markerRenderGeneration || map == null || map.getStyle() == null) {
			return;
		}
		GeoJsonSource source = map.getStyle().getSourceAs(POI_SOURCE_ID);
		if (source != null) {
			List<String> features = new ArrayList<>(renderedHullLabelFeatures);
			features.addAll(pendingPoiFeatures);
			source.setGeoJson(featureCollection(features));
			renderedPois.clear();
			renderedPois.putAll(pendingRenderedPois);
		}
	}

	private static long markerId(GeoNode poi) {
		return "node".equals(poi.jsonNodeInfo.optString("type")) ? poi.osmID : -poi.osmID;
	}

	private String getPoiIconKey(DisplayableGeoNode poi) {
		StringBuilder styles = new StringBuilder();
		for (GeoNode.ClimbingStyle style : poi.geoNode.getClimbingStyles()) {
			styles.append(style.name()).append(',');
		}
		return poi.geoNode.osmID + "|" + poi.getAlpha() + "|" + poi.geoNode.getName()
				+ "|" + poi.geoNode.getNodeType().name() + "|" + styles
				+ "|" + poi.geoNode.getKey(ClimbingTags.KEY_ROUTES)
				+ "|" + poi.geoNode.getLevelId(ClimbingTags.KEY_GRADE_TAG);
	}

	private void updateObserverRotation() {
		if (rotationMode == RotationMode.AUTO) {
			observerRotationDegrees = 0;
		} else {
			observerRotationDegrees = (float) (lastSensorHeadingDegrees + currentBearing());
		}
		updateObserverMarker();
	}

	private void updateObserverMarker() {
		updatePointSource(OBSERVER_SOURCE_ID, observerLocation,
				"\"" + ROTATION_PROPERTY + "\":" + observerRotationDegrees);
	}

	private void updateTapMarker() {
		updatePointSource(TAP_SOURCE_ID, tapLocation);
	}

	private void updateViewCone() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		GeoJsonSource coneSource = map.getStyle().getSourceAs(VIEW_CONE_SOURCE_ID);
		GeoJsonSource rangeSource = map.getStyle().getSourceAs(VIEW_RANGE_SOURCE_ID);
		if (coneSource == null || rangeSource == null) {
			return;
		}

		if (viewConeFieldOfViewDegrees <= 0 || viewConeRangeMeters <= 0) {
			coneSource.setGeoJson(EMPTY_FEATURE_COLLECTION);
			rangeSource.setGeoJson(EMPTY_FEATURE_COLLECTION);
			renderedViewConeCenter = null;
			renderedViewConeHeadingDegrees = Double.NaN;
			return;
		}

		// The heading arrives at sensor rate, so the shapes are only rebuilt once the observer
		// actually turned or moved far enough for the difference to be visible.
		boolean shapeChanged = renderedViewConeCenter == null
				|| renderedViewConeCenter.getLatitude() != observerLocation.getLatitude()
				|| renderedViewConeCenter.getLongitude() != observerLocation.getLongitude()
				|| renderedViewConeRangeMeters != viewConeRangeMeters;
		boolean coneChanged = shapeChanged
				|| renderedViewConeFieldOfViewDegrees != viewConeFieldOfViewDegrees
				|| Double.isNaN(renderedViewConeHeadingDegrees)
				|| Math.abs(GeoUtils.diffAngle(lastSensorHeadingDegrees,
				renderedViewConeHeadingDegrees)) >= VIEW_CONE_HEADING_EPSILON_DEGREES;

		if (coneChanged) {
			coneSource.setGeoJson(buildViewConeGeoJson());
			renderedViewConeHeadingDegrees = lastSensorHeadingDegrees;
		}
		if (shapeChanged) {
			rangeSource.setGeoJson(buildViewRangeGeoJson());
		}
		renderedViewConeCenter = observerLocation;
		renderedViewConeFieldOfViewDegrees = viewConeFieldOfViewDegrees;
		renderedViewConeRangeMeters = viewConeRangeMeters;
	}

	private String buildViewConeGeoJson() {
		double fieldOfView = Math.min(viewConeFieldOfViewDegrees, 360);
		double startBearing = lastSensorHeadingDegrees - (fieldOfView / 2.0);
		StringBuilder ring = new StringBuilder();
		appendObserverOffset(ring, 0, 0);
		for (int segment = 0; segment <= VIEW_CONE_ARC_SEGMENTS; segment++) {
			appendObserverOffset(ring,
					startBearing + (fieldOfView * segment / VIEW_CONE_ARC_SEGMENTS),
					viewConeRangeMeters);
		}
		appendObserverOffset(ring, 0, 0);
		return featureCollection(Collections.singletonList(
				"{\"type\":\"Feature\",\"properties\":{},"
						+ "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" + ring + "]]}}"));
	}

	private String buildViewRangeGeoJson() {
		StringBuilder ring = new StringBuilder();
		for (int segment = 0; segment <= VIEW_RANGE_CIRCLE_SEGMENTS; segment++) {
			appendObserverOffset(ring, 360.0 * segment / VIEW_RANGE_CIRCLE_SEGMENTS,
					viewConeRangeMeters);
		}
		return featureCollection(Collections.singletonList(
				"{\"type\":\"Feature\",\"properties\":{},"
						+ "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" + ring + "]}}"));
	}

	/**
	 * Appends the coordinate found by walking the given distance from the observer along the given
	 * compass bearing (0 is north, growing clockwise).
	 */
	private void appendObserverOffset(StringBuilder coordinates, double bearingDegrees,
	                                  double distanceMeters) {
		double bearing = Math.toRadians(bearingDegrees);
		Vector2d offset = GeoUtils.latLongOffset(observerLocation.getLatitude(),
				new Vector2d(distanceMeters * Math.sin(bearing),
						distanceMeters * Math.cos(bearing)));
		if (coordinates.length() > 0) {
			coordinates.append(',');
		}
		coordinates.append('[')
				.append(observerLocation.getLongitude() + offset.x).append(',')
				.append(observerLocation.getLatitude() + offset.y).append(']');
	}

	private void updatePointSource(String sourceId, MapCoordinate coordinate) {
		updatePointSource(sourceId, coordinate, "");
	}

	private void updatePointSource(String sourceId, MapCoordinate coordinate, String properties) {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		GeoJsonSource source = map.getStyle().getSourceAs(sourceId);
		if (source != null) {
			source.setGeoJson("{\"type\":\"FeatureCollection\"," +
					"\"features\":[{\"type\":\"Feature\","
					+ "\"properties\":{" + properties +
					"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
					+ coordinate.getLongitude() + "," + coordinate.getLatitude() + "]}}]}");
		}
	}

	private String featureCollection(List<String> features) {
		StringBuilder result = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
		for (int index = 0; index < features.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append(features.get(index));
		}
		return result.append("]}").toString();
	}

	private Bitmap bitmapFromDrawable(int drawableId) {
		return bitmapFromDrawable(
				ResourcesCompat.getDrawable(parent.getResources(), drawableId, null));
	}

	private Bitmap bitmapFromDrawable(Drawable drawable) {
		Bitmap bitmap =
				Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
						Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);
		return bitmap;
	}

	public void setMapAutoFollow(boolean enabled) {
		setFollowObserver(enabled);
	}

	public void setOnMapClickListener(OnMapClickListener listener) {
		onMapClickListener = listener;
	}

	public void setOnTouchListener(View.OnTouchListener listener) {
		mapView.setOnTouchListener(listener);
	}

	public void setEditMarker(DisplayableGeoNode poi) {
		editMarkerPoi = poi;
		renderEditMarker();
	}

	private void renderEditMarker() {
		if (!styleLoaded || map == null || map.getStyle() == null || editMarkerPoi == null) {
			return;
		}
		Style style = map.getStyle();
		if (style.getImage(EDIT_IMAGE_ID) != null) {
			style.removeImage(EDIT_IMAGE_ID);
		}
		Drawable drawable = new PoiMarkerDrawable(parent, editMarkerPoi,
				editMarkerPoi.getAlpha()).getDrawable();
		style.addImage(EDIT_IMAGE_ID, bitmapFromDrawable(drawable));
		updatePointSource(EDIT_SOURCE_ID, new MapCoordinate(
				editMarkerPoi.geoNode.decimalLatitude,
				editMarkerPoi.geoNode.decimalLongitude,
				editMarkerPoi.geoNode.elevationMeters));
	}

	private void setFollowObserver(boolean enabled) {
		followObserver = enabled;
		if (enabled) {
			refreshOnNextCameraIdle = true;
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
		int icon = rotationMode == RotationMode.USER ? R.drawable.ic_compass_user :
				R.drawable.ic_compass;
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

	private enum RotationMode {
		STATIC, AUTO, USER
	}

	public interface OnMapClickListener {
		void onMapClick(MapCoordinate coordinate);
	}
}
