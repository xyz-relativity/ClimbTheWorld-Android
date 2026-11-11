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
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
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
import com.climbtheworld.app.map.widget.climbing.RouteNumbers;
import com.climbtheworld.app.map.widget.climbing.RouteRelation;
import com.climbtheworld.app.map.widget.climbing.RouteStandIn;
import com.climbtheworld.app.map.widget.climbing.RouteWay;
import com.climbtheworld.app.navigate.widgets.CompassBlur;
import com.climbtheworld.app.navigate.widgets.CompassStatusBadge;
import com.climbtheworld.app.sensors.orientation.HeadingConfidence;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.CachedDataManager;
import com.climbtheworld.app.storage.TerrainElevation;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.ClusterDialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdate;
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
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonOptions;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.RasterDemSource;
import org.maplibre.android.style.sources.TileSet;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import needle.UiRelatedTask;

/**
 * MapLibre implementation for the primary map screen.
 */
public class MapLibreMapWidget {
	/**
	 * Washes out a button whose mode is off: GPS centering here, and ARCore's aiming tools button.
	 */
	public static final int BUTTON_OFF_TINT = Color.parseColor("#aaffffff");
	private static final double DEFAULT_ZOOM_LEVEL = 16;
	private static final long AMBIENT_CACHE_SIZE_BYTES = 300L * 1024L * 1024L;
	private static final double CENTER_ON_LOCATION_ZOOM_LEVEL = 24;
	private static final double POI_RENDER_MIN_ZOOM_LEVEL = MapZoomLevels.POI_AND_ROUTE_MIN;
	private static final int MAX_CACHED_POI_ICONS = 200;
	private static final float HULL_OUTLINE_WIDTH_DP = 1f;
	// The hull polygons, which the fill layer fills and the outline layer draws the rings of.
	private static final String HULL_SOURCE_ID = "ctw-hull-source";
	private static final String HULL_FILL_LAYER_ID = "ctw-hull-fill-layer";
	private static final String HULL_OUTLINE_LAYER_ID = "ctw-hull-outline-layer";
	private static final String HULL_FILL_COLOR_PROPERTY = "fillColor";
	private static final String WAY_SOURCE_ID = "ctw-way-source";
	private static final String WAY_CASING_LAYER_ID = "ctw-way-casing-layer";
	private static final String WAY_LAYER_ID = "ctw-way-layer";
	private static final float WAY_WIDTH_DP = 2f;
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
	// How far the heading turns before the view cone and the observer arrow are drawn again, see
	// headingChanged.
	private static final double HEADING_EPSILON_DEGREES = 1;
	// How far the heading turns before a map following the compass turns with it. While the
	// device turns, each reading is further than this from the last, so the map turns with every
	// one of them, as smoothly as before: holding readings back by time made it choppy. This only
	// leaves out what a device lying still jitters by, which no one sees (at this angle, the
	// corner of the screen moves by about a pixel) but which kept the map drawing, saving its
	// camera and checking its data again, 50 times a second.
	private static final double CAMERA_BEARING_EPSILON_DEGREES = 0.05;
	private static final int FIT_RADIUS_PADDING_DP = 8;
	// Fraction of the visible span loaded beyond each edge of the view.
	private static final double LOAD_BOUNDS_PADDING = 0.25;
	private static final String POI_SOURCE_ID = "ctw-poi-source";
	private static final String POI_LAYER_ID = "ctw-poi-layer";
	private static final String OBSERVER_SOURCE_ID = "ctw-observer-source";
	private static final String OBSERVER_LAYER_ID = "ctw-observer-layer";
	private static final String HEADING_ACCURACY_LAYER_ID = "ctw-heading-accuracy-layer";
	private static final String HEADING_ACCURACY_IMAGE_PREFIX = "ctw-heading-accuracy-";
	private static final String HEADING_ACCURACY_IMAGE_PROPERTY = "headingAccuracyImage";
	// Also the beam over the arrow in the compass calibration, see CompassCalibrationView.
	public static final int HEADING_ACCURACY_COLOR = 0x802196f3;
	public static final float HEADING_ACCURACY_RADIUS_DP = 48f;
	// Wedges are only drawn for widths in whole steps, so that few images are made.
	private static final int HEADING_ACCURACY_STEP_DEGREES = 5;
	private static final String TAP_SOURCE_ID = "ctw-tap-source";
	private static final String TAP_LAYER_ID = "ctw-tap-layer";
	private static final String EDIT_SOURCE_ID = "ctw-edit-source";
	private static final String EDIT_LAYER_ID = "ctw-edit-layer";
	private static final String EDIT_IMAGE_ID = "ctw-edit-image";
	private static final String CLUSTER_SOURCE_ID = "ctw-cluster-source";
	private static final String CLUSTER_LAYER_ID = "ctw-cluster-layer";
	private static final String CLUSTER_IMAGE_ID = "ctw-cluster-image";
	// With the filter badge, while the clusters count only the POIs matching the display filters.
	private static final String CLUSTER_FILTERED_IMAGE_ID = "ctw-cluster-filtered-image";
	private static final String TERRAIN_SOURCE_ID = "ctw-terrain-source";
	private static final String CLUSTER_COUNT_PROPERTY = "point_count";
	private static final String CLUSTER_COUNT_LABEL_PROPERTY = "point_count_abbreviated";
	private static final float CLUSTER_TEXT_SIZE_SP = 14f;
	private static final String ICON_PROPERTY = "icon";
	private static final String POI_ID_PROPERTY = "poiId";
	private static final String ROTATION_PROPERTY = "rotation";
	private static final String OBSERVER_IMAGE_ID = "ctw-observer-image";
	private static final String OBSERVER_NO_HEADING_IMAGE_ID = "ctw-observer-no-heading-image";
	private static final String TAP_IMAGE_ID = "ctw-tap-image";
	private static final String EMPTY_FEATURE_COLLECTION =
			"{\"type\":\"FeatureCollection\",\"features\":[]}";
	private static final float MANUAL_ROTATION_DEADBAND_DEGREES = 12f;
	// The zoom levels at which more is loaded for the view: the routes are numbered from CRAG_MIN,
	// see invalidateData. Crossing one needs the database again, see refreshForView.
	private static final List<Float> RELOAD_ZOOM_LEVELS =
			Collections.singletonList(MapZoomLevels.CRAG_MIN);
	private static boolean ambientCacheConfigured;
	private static final Map<Profile, SessionState> sessionStates = new EnumMap<>(Profile.class);
	private final AppCompatActivity parent;
	// The map and its buttons. Buttons are only looked up in it, as an activity may have more
	// than one map, such as the node editor's and its A.R. placement's minimap.
	private final View container;
	private final Configs configs;
	private final SessionState session;
	private final MapView mapView;
	private final View loadingIndicator;
	private final ClimbingGeometryBuilder climbingGeometryBuilder = new ClimbingGeometryBuilder();
	private final Map<Long, DisplayableGeoNode> visiblePois = new ConcurrentHashMap<>();
	private final Map<Long, DisplayableGeoNode> renderedPois = new HashMap<>();
	private final Map<String, ClimbingGeometryBuilder.GeometrySpec> renderedHullLabels =
			new HashMap<>();
	// The pins drawn, by what they are drawn from (see poiIconKey), the least recently used
	// dropped first. Only touched on the map executor's thread, see preparePoiFeature.
	private final Map<String, Bitmap> poiBitmaps = lruCache(MAX_CACHED_POI_ICONS);
	// The images in the style, pruned of what the sources no longer use once they are set, see
	// publishPoiFeatures.
	private final Set<String> registeredPoiImages = new HashSet<>();
	private final Set<String> registeredHeadingAccuracyImages = new HashSet<>();
	private final Set<String> registeredHullLabelImages = new HashSet<>();
	// The label images the rendered hull label features use, see registerHullLabelImages.
	private Set<String> renderedHullLabelImages = Collections.emptySet();
	private final boolean forceGhostPois;
	private final boolean showTapMarker;
	private final boolean creditTerrain;
	private List<ClimbingGeometryBuilder.GeometrySpec> pendingClimbingGeometry =
			Collections.emptyList();
	private String pendingHullGeoJson = EMPTY_FEATURE_COLLECTION;
	private List<String> pendingHullLabelFeatures = Collections.emptyList();
	private List<String> renderedHullLabelFeatures = Collections.emptyList();
	private String pendingWayGeoJson = EMPTY_FEATURE_COLLECTION;
	private String pendingClusterGeoJson = EMPTY_FEATURE_COLLECTION;
	// The POIs in the cluster source, by the marker id each cluster leaf carries.
	private Map<Long, DisplayableGeoNode> pendingClusterPois = Collections.emptyMap();
	// Whether the clusters count only the POIs matching the display filters, and wear the filter
	// badge for it.
	private boolean pendingClusterFiltered;
	private Map<Long, DisplayableGeoNode> renderedClusterPois = Collections.emptyMap();
	// The zoom the climbing geometry is prepared for, see prepareClimbingGeometry.
	private double pendingClimbingZoom;
	// The zoom levels at which what is drawn of the loaded data changes, see refreshForView.
	private Set<Float> displayZoomLevels = Collections.emptySet();
	// The route relations loaded, by the IDs of their pitches, see isRouteGhost.
	private Map<Long, RouteRelation> loadedPitchRoutes = Collections.emptyMap();
	private MapLibreMap map;
	private UiRelatedTask<Boolean> updateTask;
	private int markerRenderGeneration;
	// Draws the pins off the UI thread, see renderMarkers.
	private UiRelatedTask<PreparedPins> markerTask;
	private MapCoordinate observerLocation;
	private MapCoordinate tapLocation;
	// Unknown until the first heading arrives, and for good on devices without a compass.
	private double lastSensorHeadingDegrees = Double.NaN;
	// How far off that heading may be, NaN when the device does not tell.
	private double lastHeadingAccuracyDegrees = Double.NaN;
	// Null in containers whose compass button has no badge.
	private CompassStatusBadge compassStatusBadge;
	private CompassAccuracyArcDrawable compassAccuracyArc;
	// Blurs the compass button's needle while the heading is not to be trusted, as each
	// orientation reading tells, see onOrientationChange and CompassBlur.
	private CompassBlur compassBlur;
	private double viewConeFieldOfViewDegrees;
	private double viewConeRangeMeters;
	private double initialFitRadiusMeters;
	private boolean initialFitPending;
	private boolean initialFitWaitingForLayout;
	private MapCoordinate renderedViewConeCenter;
	private double renderedViewConeHeadingDegrees = Double.NaN;
	private double renderedViewConeFieldOfViewDegrees;
	private double renderedViewConeRangeMeters;
	// What the observer marker shows, so that it is only sent again once it would look
	// different, see updateObserverMarker. Null, NaN and null before it is first drawn.
	private MapCoordinate renderedObserverLocation;
	private double renderedObserverHeadingDegrees = Double.NaN;
	private String renderedObserverAccuracyImage;
	private List<DisplayableGeoNode> editMarkerPois = new ArrayList<>();
	private int editMarkerImageCount = 0;
	private OnMapClickListener onMapClickListener;
	private OnPoiClickListener onPoiClickListener;
	private OnCollectionClickListener onCollectionClickListener;
	private OnFollowCompassChangeListener onFollowCompassChangeListener;
	private boolean followObserver;
	private boolean automaticCameraMove;
	// The style shown, or loading, null before the first.
	private String loadedStyleId;
	// The area and zoom the data is loaded for, or being loaded for, see invalidateData.
	private MapBounds loadedBounds;
	private double loadedZoom;
	// Whether the data for loadedBounds is loaded and drawn, so a zoom can be drawn from it.
	private boolean dataLoaded;
	// Whether a task loads the data for the view, or prepares the loaded data for a zoom.
	private boolean dataTaskRunning;
	// Where the camera was when the pins were drawn, and whether the pin count limit left some
	// out: it keeps the pins nearest the centre, so a move changes which show.
	private LatLng renderedCenter;
	private boolean renderedPoisCapped;
	private boolean styleLoaded;
	private MapLibreMap.CancelableCallback buttonAnimation;
	private RotationMode rotationMode;

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState) {
		this(parent, container, savedInstanceState, Profile.DEFAULT);
	}

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState,
	                         Profile profile) {
		this(parent, container, savedInstanceState, profile, false, true);
	}

	public MapLibreMapWidget(AppCompatActivity parent, View container, Bundle savedInstanceState,
	                         Profile profile, boolean forceGhostPois, boolean showTapMarker) {
		this.parent = parent;
		this.container = container;
		this.configs = Configs.instance(parent);
		this.session = sessionStates.computeIfAbsent(profile, SessionState::new);
		this.followObserver = session.followObserver;
		this.rotationMode = session.rotationMode;
		this.forceGhostPois = forceGhostPois;
		this.showTapMarker = showTapMarker;
		this.creditTerrain = profile.creditTerrain;
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

	static boolean isPoiVisibleAtZoom(GeoNode poi, double zoom) {
		return zoom >= POI_RENDER_MIN_ZOOM_LEVEL
				|| (zoom >= MapZoomLevels.AREA_MIN
				&& poi.getNodeType() == OsmEntity.EntityClimbingType.artificial);
	}

	private static long markerId(GeoNode poi) {
		return isNode(poi) ? poi.osmID : -poi.osmID;
	}

	private static boolean isNode(GeoNode poi) {
		return "node".equals(poi.jsonNodeInfo.optString("type"));
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
			if (onClusterClick(screenPoint)) {
				return true;
			}
			List<Feature> features = map.queryRenderedFeatures(screenPoint, POI_LAYER_ID);
			if (!features.isEmpty() && features.get(0).hasProperty(POI_ID_PROPERTY)) {
				long poiId = features.get(0).getNumberProperty(POI_ID_PROPERTY).longValue();
				DisplayableGeoNode poi = renderedPois.get(poiId);
				// The top of a route, and the start of a route relation, leave the details to the
				// route, as in the AR view.
				if (poi != null && poi.geoNode instanceof RouteStandIn) {
					poi = new DisplayableGeoNode(((RouteStandIn) poi.geoNode).route,
							poi.isShowPoiInfoDialog());
				}
				if (poi != null && onPoiClickListener != null) {
					onPoiClickListener.onPoiClick(poi);
					return true;
				}
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
						&& onCollectionClickListener != null) {
					onCollectionClickListener.onCollectionClick(geometry.collection);
					return true;
				}
				if (geometry != null && geometry.collection != null
						&& geometry.labelCoordinate != null) {
					NodeDialogBuilder.showCollectionInfoDialog(parent, geometry.collection,
							geometry.labelCoordinate);
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
				automaticCameraMove = false;
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
			boolean movedByUser = !automaticCameraMove;
			automaticCameraMove = false;
			refreshForView(movedByUser);
		});
		loadSelectedStyle();
	}

	private void configureControls(View container) {
		View layerButton = container.findViewById(R.id.mapLayerToggleButton);
		if (layerButton != null) {
			layerButton.setOnClickListener(view -> selectNextStyle());
		}

		ImageView locationButton = container.findViewById(R.id.mapCenterOnGpsButton);
		if (locationButton != null) {
			locationButton.setOnClickListener(view -> setFollowObserver(true));
		}

		View zoomInButton = container.findViewById(R.id.mapZoomInButton);
		if (zoomInButton != null) {
			zoomInButton.setOnClickListener(
					view -> animateFromButton(CameraUpdateFactory.zoomIn()));
		}

		View zoomOutButton = container.findViewById(R.id.mapZoomOutButton);
		if (zoomOutButton != null) {
			zoomOutButton.setOnClickListener(
					view -> animateFromButton(CameraUpdateFactory.zoomOut()));
		}

		ImageView compassButton = container.findViewById(R.id.compassButton);
		if (compassButton != null) {
			compassButton.setOnClickListener(view -> {
				setRotationMode(RotationMode.values()[(rotationMode.ordinal() + 1) %
						RotationMode.values().length]);
			});
			compassAccuracyArc = new CompassAccuracyArcDrawable();
			compassButton.setForeground(compassAccuracyArc);
			compassBlur = new CompassBlur(compassButton);
		}

		ImageView badge = container.findViewById(R.id.compassStatusBadge);
		if (badge != null) {
			compassStatusBadge = new CompassStatusBadge(badge);
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
		loadedStyleId = style.getId();
		map.setStyle(style.getStyleUrl(), loadedStyle -> {
			styleLoaded = true;
			registeredPoiImages.clear();
			registeredHeadingAccuracyImages.clear();
			registeredHullLabelImages.clear();
			renderedHullLabelImages = Collections.emptySet();
			renderedPois.clear();
			renderedHullLabels.clear();
			renderedHullLabelFeatures = Collections.emptyList();
			renderedViewConeCenter = null;
			renderedViewConeHeadingDegrees = Double.NaN;
			renderedObserverLocation = null;
			renderedObserverHeadingDegrees = Double.NaN;
			renderedObserverAccuracyImage = null;
			initializeOverlayLayers(loadedStyle);
			applyCamera(session.camera != null ? session.camera
					: new MapCameraState(observerLocation, DEFAULT_ZOOM_LEVEL), false);
			applyInitialFit();
			applyRotationMode();
			renderMarkers();
			invalidateData();
		});
	}

	private void initializeOverlayLayers(Style style) {
		style.addImage(OBSERVER_IMAGE_ID, bitmapFromDrawable(R.drawable.ic_my_location));
		style.addImage(OBSERVER_NO_HEADING_IMAGE_ID,
				bitmapFromDrawable(R.drawable.ic_my_location_no_heading));
		style.addImage(TAP_IMAGE_ID, bitmapFromDrawable(R.drawable.ic_tap_marker));
		style.addImage(CLUSTER_IMAGE_ID, bitmapFromDrawable(MarkerUtils.getClusterIcon(parent,
				DisplayableGeoNode.CLUSTER_DEFAULT_COLOR,
				DisplayableGeoNode.CLUSTER_ICON_ALPHA)));
		style.addImage(CLUSTER_FILTERED_IMAGE_ID, bitmapFromDrawable(MarkerUtils.getClusterIcon(
				parent, DisplayableGeoNode.CLUSTER_DEFAULT_COLOR,
				DisplayableGeoNode.CLUSTER_ICON_ALPHA, true)));

		style.addSource(new GeoJsonSource(HULL_SOURCE_ID, EMPTY_FEATURE_COLLECTION));
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
		if (creditTerrain) {
			// No layer draws it, so no tile is fetched: the source is only there for the
			// attribution dialog, which lists the attributions of the style sources.
			TileSet terrainTiles = new TileSet("3.0.0", TerrainElevation.TILE_URL);
			terrainTiles.setAttribution(TerrainElevation.ATTRIBUTION);
			terrainTiles.setEncoding(TerrainElevation.ENCODING);
			style.addSource(new RasterDemSource(TERRAIN_SOURCE_ID, terrainTiles,
					TerrainElevation.TILE_SIZE));
		}

		style.addLayer(new FillLayer(HULL_FILL_LAYER_ID, HULL_SOURCE_ID)
				.withProperties(
						fillColor(Expression.toColor(Expression.get(HULL_FILL_COLOR_PROPERTY)))));
		style.addLayer(new LineLayer(HULL_OUTLINE_LAYER_ID, HULL_SOURCE_ID)
				.withProperties(
						lineColor(0xff000000),
						lineWidth(HULL_OUTLINE_WIDTH_DP),
						lineJoin(Property.LINE_JOIN_ROUND),
						lineCap(Property.LINE_CAP_ROUND)));
		// The routes mapped as a way are drawn as in the AR view: in the colour of their grade,
		// along a dark edge.
		Expression routeLine = Expression.has(ClimbingGeometryBuilder.WAY_CASING_COLOR_PROPERTY);
		style.addLayer(new LineLayer(WAY_CASING_LAYER_ID, WAY_SOURCE_ID)
				.withFilter(routeLine)
				.withProperties(
						lineColor(Expression.toColor(Expression.get(
								ClimbingGeometryBuilder.WAY_CASING_COLOR_PROPERTY))),
						lineWidth(DisplayableGeoNode.LINE_CASING_WIDTH_DP),
						lineJoin(Property.LINE_JOIN_ROUND),
						lineCap(Property.LINE_CAP_ROUND)));
		style.addLayer(new LineLayer(WAY_LAYER_ID, WAY_SOURCE_ID)
				.withProperties(
						lineColor(Expression.toColor(Expression.get(
								ClimbingGeometryBuilder.WAY_COLOR_PROPERTY))),
						lineWidth(Expression.switchCase(routeLine,
								Expression.literal(DisplayableGeoNode.LINE_WIDTH_DP),
								Expression.literal(WAY_WIDTH_DP))),
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
		// The location and tap markers sit above the hulls but under the POIs, so they never hide
		// a pin. How far off the heading may be fans out under the location arrow, and turns with
		// it.
		style.addLayer(new SymbolLayer(HEADING_ACCURACY_LAYER_ID, OBSERVER_SOURCE_ID)
				.withFilter(Expression.has(HEADING_ACCURACY_IMAGE_PROPERTY))
				.withProperties(
						iconImage(Expression.get(HEADING_ACCURACY_IMAGE_PROPERTY)),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						iconRotate(Expression.get(ROTATION_PROPERTY)),
						iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		style.addLayer(new SymbolLayer(OBSERVER_LAYER_ID, OBSERVER_SOURCE_ID)
				.withProperties(
						// Without a heading, a dot instead of an arrow pointing nowhere in
						// particular.
						iconImage(Expression.switchCase(
								Expression.has(ROTATION_PROPERTY),
								Expression.literal(OBSERVER_IMAGE_ID),
								Expression.literal(OBSERVER_NO_HEADING_IMAGE_ID))),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						// The rotation is the compass heading, so the arrow is aligned to the map
						// and turns with it whatever the map's own rotation.
						iconRotate(Expression.get(ROTATION_PROPERTY)),
						iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		style.addLayer(new SymbolLayer(TAP_LAYER_ID, TAP_SOURCE_ID)
				.withProperties(
						iconImage(TAP_IMAGE_ID),
						iconAnchor(Property.ICON_ANCHOR_CENTER),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		style.addLayer(new SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID)
				.withProperties(
						// POI pins and relation label pins share this layer so they depth-sort
						// together.
						iconImage(Expression.coalesce(Expression.get(ICON_PROPERTY),
								Expression.get(ClimbingGeometryBuilder.LABEL_ICON_PROPERTY))),
						iconAnchor(Property.ICON_ANCHOR_BOTTOM),
						iconAllowOverlap(true),
						iconIgnorePlacement(true),
						// Pins lower on screen (closer to the viewer) draw over the ones behind
						// them.
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
		style.addLayer(new SymbolLayer(EDIT_LAYER_ID, EDIT_SOURCE_ID)
				.withProperties(
						iconImage(Expression.get(ICON_PROPERTY)),
						iconAnchor(Property.ICON_ANCHOR_BOTTOM),
						iconAllowOverlap(true),
						iconIgnorePlacement(true)));
		editMarkerImageCount = 0;
		renderEditMarkers();
		updateViewCone();
	}

	/**
	 * Shows the style selected now, when another map changed it since this one loaded its own.
	 */
	public void reloadStyleIfChanged() {
		// Without a map yet, it loads the selected style once it has one.
		if (map == null || loadedStyleId == null) {
			return;
		}
		if (!loadedStyleId.equals(MapStyleRegistry.getStyle(
				configs.getString(Configs.ConfigKey.mapStyleId)).getId())) {
			styleLoaded = false;
			loadSelectedStyle();
		}
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
		boolean followedCompass = rotationMode == RotationMode.AUTO;
		boolean followsCompass = mode == RotationMode.AUTO;
		rotationMode = mode;
		session.rotationMode = mode;
		applyRotationMode();
		if (onFollowCompassChangeListener != null && followsCompass != followedCompass) {
			onFollowCompassChangeListener.onFollowCompassChange(followsCompass);
		}
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
		updateViewCone();
		if (followObserver && buttonAnimation == null) {
			centerOnObserver();
			// The recentering animations keep cancelling each other while moving, so the camera
			// may not go idle until the observer stops.
			refreshForView(false);
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
	 * It is only the default: once this session has a camera for the profile it is restored
	 * instead, so the user's zoom is kept.
	 */
	public void setInitialZoomToFitRadius(double radiusMeters) {
		initialFitRadiusMeters = radiusMeters;
		initialFitPending = radiusMeters > 0 && session.camera == null;
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

	/**
	 * @param orientation the azimuth the map shows, one of those of the event
	 * @param event       the orientation it is from, for how far off it may be and whether the
	 *                    compass can be trusted at all
	 */
	public void onOrientationChange(Vector4d orientation,
	                                OrientationManager.OrientationEvent event) {
		lastSensorHeadingDegrees = orientation.x;
		lastHeadingAccuracyDegrees = event.headingAccuracyDegrees;
		if (compassStatusBadge != null) {
			compassStatusBadge.setStatus(event.compassStatus);
		}
		if (compassBlur != null) {
			compassBlur.setConfidence(HeadingConfidence.of(event.headingUncertaintyDegrees,
					compassBlur.getConfidence()));
		}
		updateCompassAccuracyArc();
		if (rotationMode == RotationMode.AUTO && map != null) {
			if (buttonAnimation == null
					&& cameraBearingChanged(map.getCameraPosition().bearing, orientation.x)) {
				rotateCamera(orientation.x);
			}
			ImageView compassButton = container.findViewById(R.id.compassButton);
			if (compassButton != null) {
				compassButton.setRotation(-(float) orientation.x);
			}
		}
		updateObserverMarker();
		updateViewCone();
	}

	public void centerOnLocation(MapCoordinate location) {
		centerOnLocation(location, CENTER_ON_LOCATION_ZOOM_LEVEL);
	}

	public void centerOnLocation(MapCoordinate location, double zoom) {
		tapLocation = location;
		setFollowObserver(false);
		if (map == null) {
			session.camera = new MapCameraState(location, zoom);
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
		MapCoordinate center = bounds.getCenter();
		double zoom = maxZoom;
		if (map != null && mapView.getWidth() > 0 && mapView.getHeight() > 0) {
			int padding = Globals.convertDpToPixel(FIT_RADIUS_PADDING_DP).intValue();
			// MapLibre takes bounds across the antimeridian with the east side past 180.
			CameraPosition fitted = map.getCameraForLatLngBounds(new LatLngBounds(
							bounds.getNorth(), bounds.getWest() + bounds.getLongitudeSpan(),
							bounds.getSouth(), bounds.getWest()),
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

	/**
	 * @return the zoom level the map shows, or will once it shows
	 */
	public double getZoom() {
		if (map != null && styleLoaded) {
			return map.getCameraPosition().zoom;
		}
		return session.camera != null ? session.camera.getZoom() : DEFAULT_ZOOM_LEVEL;
	}

	/**
	 * Zooms the map where it is and as it is turned, instead of fitting the initial radius.
	 */
	public void setZoom(double zoom) {
		initialFitPending = false;
		if (map == null || !styleLoaded) {
			// The camera the style applies once loaded.
			MapCameraState camera = session.camera;
			session.camera = camera != null
					? new MapCameraState(camera.getCenter(), zoom, camera.getBearing())
					: new MapCameraState(observerLocation, zoom);
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		moveCamera(fromLatLng(camera.target), zoom, camera.bearing, false);
	}

	public MapCoordinate getTapLocation() {
		return tapLocation;
	}

	/**
	 * Loads the data for the view from the database and draws it: the pins, clusters, hulls and
	 * lines in an area padded past the view, so that a small move needs no loading (see
	 * refreshForView). The routes are only numbered from CRAG_MIN, see RELOAD_ZOOM_LEVELS.
	 */
	public void invalidateData() {
		if (!styleLoaded || map == null) {
			return;
		}

		if (updateTask != null) {
			updateTask.cancel();
		}
		final MapBounds loadBounds = getVisibleBounds().padded(LOAD_BOUNDS_PADDING);
		loadedBounds = loadBounds;
		final double visibleZoom = map.getCameraPosition().zoom;
		loadedZoom = visibleZoom;
		dataLoaded = false;
		dataTaskRunning = true;
		setLoading(true);
		updateTask = new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				visiblePois.clear();
				// One load reads each element once, see CachedDataManager. The geometry goes
				// first: its collections and their nodes are most of what the pins, the route
				// relations and the route numbers read.
				CachedDataManager data = new CachedDataManager();
				List<ClimbingGeometryBuilder.GeometrySpec> climbingGeometry =
						climbingGeometryBuilder.load(parent, loadBounds, data);
				boolean loaded = data.loadDisplayableNodesBBox(parent, loadBounds, visiblePois);
				if (!isCanceled()) {
					List<RouteRelation> routeRelations =
							RouteRelation.load(parent, loadBounds, data);
					for (RouteRelation routeRelation : routeRelations) {
						addRouteRelation(visiblePois, routeRelation);
					}
					// Their pins are loaded too: where the routes are all relations of unnamed
					// nodes, nothing else may be, and the view would not be drawn.
					loaded |= !routeRelations.isEmpty();
					Map<Long, RouteRelation> pitchRoutes = RouteRelation.byPitch(routeRelations);
					// Pins only show close in, and numbering every crag of a wide view is costly.
					Map<Long, Integer> routeNumbers = visibleZoom >= MapZoomLevels.CRAG_MIN
							? RouteNumbers.load(parent, loadBounds, data) : Collections.emptyMap();
					for (DisplayableGeoNode poi : visiblePois.values()) {
						// Only nodes take route numbers. A way's negative marker ID is not enough
						// to tell, as nodes created in the app have negative IDs too.
						Integer routeNumber = isNode(poi.geoNode)
								? routeNumbers.get(poi.geoNode.osmID) : null;
						poi.setRouteNumber(routeNumber != null ? routeNumber : 0);
					}
					loadedPitchRoutes = pitchRoutes;
					pendingClimbingGeometry = climbingGeometry;
					for (ClimbingGeometryBuilder.GeometrySpec geometry : pendingClimbingGeometry) {
						// The pitches of a route relation leave the top to the route.
						if (geometry.route != null
								&& !pitchRoutes.containsKey(geometry.collection.osmID)) {
							addRouteTop(visiblePois, geometry.route);
						}
					}
					Set<Float> zoomLevels =
							ClimbingGeometryBuilder.zoomLevels(pendingClimbingGeometry);
					// Where the pins show, and where the clusters stand in for them.
					zoomLevels.add(MapZoomLevels.POI_AND_ROUTE_MIN);
					zoomLevels.add(MapZoomLevels.AREA_MIN);
					displayZoomLevels = zoomLevels;
					prepareClimbingGeometry(visibleZoom);
				}
				return loaded || visiblePois.isEmpty() || isCanceled();
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean completed) {
				onDataPrepared(completed && !isCanceled());
			}
		};
		Constants.MAP_EXECUTOR.execute(updateTask);
	}

	/**
	 * Draws the loaded data again for the zoom the map is at, where it needs no loading: the
	 * hulls, lines and labels in view at that zoom, the clusters and the pins.
	 */
	private void redrawLoadedForZoom() {
		final double visibleZoom = map.getCameraPosition().zoom;
		dataTaskRunning = true;
		setLoading(true);
		updateTask = new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				prepareClimbingGeometry(visibleZoom);
				return !isCanceled();
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean completed) {
				onDataPrepared(completed && !isCanceled());
			}
		};
		Constants.MAP_EXECUTOR.execute(updateTask);
	}

	/**
	 * Builds what the layers draw of the loaded climbing geometry and POIs at the zoom. Runs off
	 * the UI thread, after the data is loaded.
	 */
	private void prepareClimbingGeometry(double zoom) {
		pendingClimbingZoom = zoom;
		pendingHullGeoJson = climbingGeometryBuilder.buildHullGeoJson(
				pendingClimbingGeometry, zoom);
		pendingHullLabelFeatures = climbingGeometryBuilder.buildHullLabelFeatures(
				pendingClimbingGeometry, zoom);
		pendingWayGeoJson = climbingGeometryBuilder.buildWayGeoJson(
				pendingClimbingGeometry, zoom,
				geometry -> isRouteGhost(geometry, loadedPitchRoutes));
		pendingClusterGeoJson = buildClusterGeoJson(zoom);
	}

	/**
	 * Draws what a task loaded or prepared, then checks the view it is for is still the one
	 * shown: the camera may have moved on meanwhile.
	 */
	private void onDataPrepared(boolean completed) {
		dataTaskRunning = false;
		if (completed) {
			dataLoaded = true;
			renderClimbingGeometry();
			renderClusters();
			renderMarkers();
		}
		setLoading(false);
		if (completed) {
			refreshForView(false);
		}
	}

	/**
	 * Pins the top of a route mapped as a way when its bottom has a pin, as the AR view does: in
	 * the colour of the grade, in place of a route_top pin on the same node. The top goes with the
	 * line, so it shows even when the bottom of a long route is beyond the loaded area.
	 */
	static void addRouteTop(Map<Long, DisplayableGeoNode> pois, RouteWay route) {
		if (route.bottomPinned) {
			addTop(pois, route.top);
		}
	}

	/**
	 * Pins a route relation, as the AR view does: the route at its start, in place of any pin on
	 * the same node, and its top as that of a route mapped as a way, see addRouteTop.
	 */
	static void addRouteRelation(Map<Long, DisplayableGeoNode> pois, RouteRelation route) {
		pois.put(markerId(route.start), new DisplayableGeoNode(route.start));
		addTop(pois, route.top);
	}

	private static void addTop(Map<Long, DisplayableGeoNode> pois, RouteWay.Top top) {
		DisplayableGeoNode pinned = pois.get(markerId(top));
		// The top of a pitch can be the bottom of the next one, which keeps its pin.
		if (pinned == null || pinned.geoNode.isRouteTop()) {
			pois.put(markerId(top), new DisplayableGeoNode(top));
		}
	}

	/**
	 * Whether the POI shows as a ghost. The top of a route, and the start of a route relation, go
	 * with the route.
	 */
	private boolean isGhost(GeoNode poi) {
		return forceGhostPois
				|| !NodeDisplayFilters.matchFilters(configs, RouteStandIn.routeOf(poi));
	}

	/**
	 * Whether the line of a route mapped as a way fades out, along with its pins. Without a pin at
	 * its bottom, the way itself holds the details of the route. A pitch of a route relation goes
	 * with the route.
	 *
	 * @param pitchRoutes the route relations by the IDs of their pitches
	 */
	private boolean isRouteGhost(ClimbingGeometryBuilder.GeometrySpec geometry,
	                             Map<Long, RouteRelation> pitchRoutes) {
		RouteRelation routeRelation = pitchRoutes.get(geometry.collection.osmID);
		if (routeRelation != null) {
			return isGhost(routeRelation.route);
		}
		RouteWay route = geometry.route;
		return isGhost(route.bottomPinned ? route.bottom
				: new GeoNode(geometry.collection.jsonNodeInfo));
	}

	/**
	 * Shows the data for the view as it is now, loading it again only when needed: once the view
	 * leaves the padded area loaded last time, or the zoom crosses a level at which more is
	 * loaded. A zoom crossing a level at which what is drawn of the loaded data changes is drawn
	 * from it. Following the observer or the compass moves the camera many times a second, and
	 * a pan or a zoom seldom needs the database again.
	 *
	 * @param movedByUser whether the user moved the camera, as opposed to the sensors or a
	 *                    task finishing: a pan changes which pins the count limit keeps
	 */
	private void refreshForView(boolean movedByUser) {
		if (!styleLoaded || map == null) {
			return;
		}
		MapBounds visible = getVisibleBounds();
		double zoom = map.getCameraPosition().zoom;
		boolean covered = loadedBounds != null
				&& loadedBounds.contains(new MapCoordinate(visible.getNorth(), visible.getEast()))
				&& loadedBounds.contains(new MapCoordinate(visible.getSouth(), visible.getWest()))
				&& !MapZoomLevels.crosses(loadedZoom, zoom, RELOAD_ZOOM_LEVELS);
		if (!covered || (!dataTaskRunning && !dataLoaded)) {
			invalidateData();
			return;
		}
		if (dataTaskRunning) {
			// What the task prepares covers the view; it is checked again once drawn, see
			// onDataPrepared.
			return;
		}
		if (MapZoomLevels.crosses(pendingClimbingZoom, zoom, displayZoomLevels)) {
			redrawLoadedForZoom();
		} else if (movedByUser && renderedPoisCapped
				&& !map.getCameraPosition().target.equals(renderedCenter)) {
			renderMarkers();
		}
	}

	/** The part of the map on screen, or null before the map is ready. */
	public MapBounds getVisibleBounds() {
		if (map == null) {
			return null;
		}
		LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
		return MapBounds.fromUnwrapped(bounds.getLatNorth(), bounds.getLonEast(),
				bounds.getLatSouth(), bounds.getLonWest());
	}

	/**
	 * Also fills {@link #pendingClusterPois}, so a tapped cluster's leaves can be resolved back to
	 * their POIs. The POIs a display filter fades out are left out, so the clusters count only
	 * those that match, and wear the filter badge for it.
	 */
	private String buildClusterGeoJson(double zoom) {
		Map<Long, DisplayableGeoNode> clusterPois = new HashMap<>();
		List<String> features = new ArrayList<>();
		if (zoom < MapZoomLevels.AREA_MIN) {
			for (DisplayableGeoNode poi : visiblePois.values()) {
				// A route counts once, by its bottom.
				if (poi.geoNode instanceof RouteWay.Top) {
					continue;
				}
				// The start of a route relation goes with the route, as its pin does.
				if (!forceGhostPois && isGhost(poi.geoNode)) {
					continue;
				}
				long markerId = markerId(poi.geoNode);
				clusterPois.put(markerId, poi);
				features.add("{\"type\":\"Feature\",\"properties\":{\"" + POI_ID_PROPERTY + "\":"
						+ markerId + "},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
						+ poi.geoNode.decimalLongitude + "," + poi.geoNode.decimalLatitude + "]}}");
			}
		}
		pendingClusterPois = clusterPois;
		pendingClusterFiltered = !forceGhostPois && NodeDisplayFilters.hasFilters(configs);
		return ClimbingGeometryBuilder.featureCollection(features);
	}

	private void renderClusters() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		GeoJsonSource source = map.getStyle().getSourceAs(CLUSTER_SOURCE_ID);
		if (source != null) {
			source.setGeoJson(pendingClusterGeoJson);
			renderedClusterPois = pendingClusterPois;
		}
		Layer clusterLayer = map.getStyle().getLayer(CLUSTER_LAYER_ID);
		if (clusterLayer != null) {
			clusterLayer.setProperties(iconImage(pendingClusterFiltered
					? CLUSTER_FILTERED_IMAGE_ID : CLUSTER_IMAGE_ID));
		}
	}

	/**
	 * Lists what the tapped cluster holds, with zooming into it offered from the list. Where POIs
	 * open no dialog (the node editor), the tap zooms in straight away.
	 */
	private boolean onClusterClick(PointF screenPoint) {
		if (map.getCameraPosition().zoom >= MapZoomLevels.AREA_MIN || map.getStyle() == null) {
			return false;
		}
		List<Feature> clusters = map.queryRenderedFeatures(screenPoint, CLUSTER_LAYER_ID);
		if (clusters.isEmpty() || !(clusters.get(0).geometry() instanceof Point)) {
			return false;
		}
		Feature cluster = clusters.get(0);
		Runnable zoomIn = buildClusterZoomIn(cluster);
		List<DisplayableGeoNode> pois =
				forceGhostPois ? Collections.emptyList() : getClusterPois(cluster);
		if (pois.isEmpty()) {
			zoomIn.run();
		} else {
			ClusterDialogBuilder.showClusterDialog(parent, pois, zoomIn);
		}
		return true;
	}

	/**
	 * The zoom is worked out now: the cluster may be gone from the source by the time the user
	 * asks for it.
	 */
	private Runnable buildClusterZoomIn(Feature cluster) {
		Point center = (Point) cluster.geometry();
		double targetZoom = MapZoomLevels.AREA_MIN;
		GeoJsonSource source = map.getStyle().getSourceAs(CLUSTER_SOURCE_ID);
		if (source != null && cluster.hasProperty(CLUSTER_COUNT_PROPERTY)) {
			targetZoom = Math.min(source.getClusterExpansionZoom(cluster), targetZoom);
		}
		MapCoordinate target = new MapCoordinate(center.latitude(), center.longitude());
		double zoom = targetZoom;
		return () -> {
			setFollowObserver(false);
			moveCamera(target, zoom, currentBearing(), true);
		};
	}

	private List<DisplayableGeoNode> getClusterPois(Feature cluster) {
		List<Feature> leaves = Collections.singletonList(cluster);
		if (cluster.hasProperty(CLUSTER_COUNT_PROPERTY)) {
			GeoJsonSource source = map.getStyle().getSourceAs(CLUSTER_SOURCE_ID);
			if (source == null) {
				return Collections.emptyList();
			}
			leaves = source.getClusterLeaves(cluster,
					cluster.getNumberProperty(CLUSTER_COUNT_PROPERTY).longValue(), 0).features();
			if (leaves == null) {
				return Collections.emptyList();
			}
		}
		List<DisplayableGeoNode> result = new ArrayList<>();
		for (Feature leaf : leaves) {
			if (!leaf.hasProperty(POI_ID_PROPERTY)) {
				continue;
			}
			DisplayableGeoNode poi = renderedClusterPois.get(
					leaf.getNumberProperty(POI_ID_PROPERTY).longValue());
			if (poi != null) {
				result.add(poi);
			}
		}
		return result;
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
		GeoJsonSource hullSource = style.getSourceAs(HULL_SOURCE_ID);
		GeoJsonSource waySource = style.getSourceAs(WAY_SOURCE_ID);
		if (hullSource != null) {
			hullSource.setGeoJson(pendingHullGeoJson);
		}
		renderedHullLabelFeatures = pendingHullLabelFeatures;
		if (waySource != null) {
			waySource.setGeoJson(pendingWayGeoJson);
		}
	}

	private void registerHullLabelImages(Style style) {
		Set<String> usedImages = new HashSet<>();
		for (ClimbingGeometryBuilder.GeometrySpec geometry : pendingClimbingGeometry) {
			if (!geometry.isLabelVisibleAt(pendingClimbingZoom)) {
				continue;
			}
			String imageId = geometry.getLabelImageId();
			usedImages.add(imageId);
			if (registeredHullLabelImages.add(imageId)) {
				style.addImage(imageId, createRelationLabelBitmap(geometry));
			}
		}
		renderedHullLabelImages = usedImages;
	}

	private Bitmap createRelationLabelBitmap(
			ClimbingGeometryBuilder.GeometrySpec geometry) {
		GeoNode relation = geometry.pin(geometry.labelCoordinate);
		return bitmapFromDrawable(new PoiMarkerDrawable(
				parent, new DisplayableGeoNode(relation)).getDrawable());
	}

	/**
	 * Draws the pins of the POIs in view and puts them on the map. Drawing them takes a while
	 * for a crag of many routes, so they are drawn off the UI thread, and go on the map all at
	 * once, see publishPoiFeatures. A newer call supersedes the pins being drawn.
	 */
	private void renderMarkers() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}

		markerRenderGeneration++;
		final int generation = markerRenderGeneration;
		final List<DisplayableGeoNode> pois = getVisiblePoisToRender();
		// The pins already in the style are not drawn again.
		final Set<String> registeredImages = new HashSet<>(registeredPoiImages);
		updateObserverMarker();
		if (showTapMarker) {
			updateTapMarker();
		}
		if (markerTask != null) {
			markerTask.cancel();
		}
		markerTask = new UiRelatedTask<PreparedPins>() {
			@Override
			protected PreparedPins doWork() {
				PreparedPins prepared = new PreparedPins();
				for (DisplayableGeoNode poi : pois) {
					if (isCanceled()) {
						break;
					}
					preparePoiFeature(poi, registeredImages, prepared);
				}
				return prepared;
			}

			@Override
			protected void thenDoUiRelatedWork(PreparedPins prepared) {
				if (!isCanceled()) {
					publishPoiFeatures(generation, prepared);
				}
			}
		};
		Constants.MAP_EXECUTOR.execute(markerTask);
	}

	private List<DisplayableGeoNode> getVisiblePoisToRender() {
		double zoom = map.getCameraPosition().zoom;
		List<DisplayableGeoNode> pois = new ArrayList<>();
		for (DisplayableGeoNode poi : visiblePois.values()) {
			if (isPoiVisibleAtZoom(poi.geoNode, zoom)) {
				pois.add(poi);
			}
		}
		LatLng center = map.getCameraPosition().target;
		int limit = configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit);
		renderedCenter = center;
		renderedPoisCapped = limit < pois.size();
		if (!renderedPoisCapped) {
			return pois;
		}
		// The loaded area extends past the view, so the cap keeps the POIs nearest its centre.
		// Each distance is worked out once: the sort compares them many times over.
		Map<DisplayableGeoNode, Double> distances = new IdentityHashMap<>();
		for (DisplayableGeoNode poi : pois) {
			distances.put(poi, center.distanceTo(
					new LatLng(poi.geoNode.decimalLatitude, poi.geoNode.decimalLongitude)));
		}
		Collections.sort(pois, Comparator.comparingDouble(distances::get));
		return new ArrayList<>(pois.subList(0, Math.max(limit, 0)));
	}

	/**
	 * Draws the pin of the POI, unless the style has it, and prepares its feature. Runs off the
	 * UI thread.
	 *
	 * @param registeredImages the pin images in the style when the drawing started
	 */
	private void preparePoiFeature(DisplayableGeoNode poi, Set<String> registeredImages,
	                               PreparedPins prepared) {
		poi.setGhost(isGhost(poi.geoNode));
		String iconKey = poiIconKey(poi);
		String imageId =
				"ctw-poi-" + poi.geoNode.osmID + "-" + Integer.toUnsignedString(iconKey.hashCode());
		if (!registeredImages.contains(imageId) && !prepared.newImages.containsKey(imageId)) {
			Bitmap bitmap = poiBitmaps.get(iconKey);
			if (bitmap == null) {
				Drawable drawable = new PoiMarkerDrawable(parent, poi,
						PoiMarkerDrawable.PinShape.slim).getDrawable();
				bitmap = bitmapFromDrawable(drawable);
				poiBitmaps.put(iconKey, bitmap);
			}
			prepared.newImages.put(imageId, bitmap);
		}
		prepared.imageIds.add(imageId);

		long markerId = markerId(poi.geoNode);
		prepared.pois.put(markerId, poi);
		prepared.features.add("{\"type\":\"Feature\",\"properties\":{\""
				+ POI_ID_PROPERTY + "\":" + markerId + ",\"" + ICON_PROPERTY
				+ "\":\"" + imageId + "\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
				+ poi.geoNode.decimalLongitude + "," + poi.geoNode.decimalLatitude + "]}}");
	}

	/** Puts the prepared pins on the map, on the UI thread, unless newer ones are being drawn. */
	private void publishPoiFeatures(int generation, PreparedPins prepared) {
		if (generation != markerRenderGeneration || map == null || map.getStyle() == null) {
			return;
		}
		Style style = map.getStyle();
		GeoJsonSource source = style.getSourceAs(POI_SOURCE_ID);
		if (source == null) {
			return;
		}
		HashMap<String, Bitmap> newImages = new HashMap<>();
		for (Map.Entry<String, Bitmap> image : prepared.newImages.entrySet()) {
			if (registeredPoiImages.add(image.getKey())) {
				newImages.put(image.getKey(), image.getValue());
			}
		}
		if (!newImages.isEmpty()) {
			style.addImages(newImages);
		}
		List<String> features = new ArrayList<>(renderedHullLabelFeatures);
		features.addAll(prepared.features);
		source.setGeoJson(ClimbingGeometryBuilder.featureCollection(features));
		renderedPois.clear();
		renderedPois.putAll(prepared.pois);
		// The style keeps every image added to it, so the pins of everywhere the map has been
		// would pile up in it. Only the images the source no longer uses go, so the pins staying
		// are drawn without a break while the new features are laid out.
		pruneImages(registeredPoiImages, prepared.imageIds, style::removeImage);
		pruneImages(registeredHullLabelImages, renderedHullLabelImages, style::removeImage);
	}

	/** The pins drawn off the UI thread for the POI source, see renderMarkers. */
	private static final class PreparedPins {
		private final List<String> features = new ArrayList<>();
		// The POIs of the features, by marker ID.
		private final Map<Long, DisplayableGeoNode> pois = new HashMap<>();
		// The images the features use, and among them those the style lacked, drawn.
		private final Set<String> imageIds = new HashSet<>();
		private final Map<String, Bitmap> newImages = new HashMap<>();
	}

	/**
	 * Removes the registered images that are not used, and forgets them.
	 *
	 * @param remove removes an image from the style
	 */
	static void pruneImages(Set<String> registered, Set<String> used, Consumer<String> remove) {
		for (Iterator<String> images = registered.iterator(); images.hasNext(); ) {
			String imageId = images.next();
			if (!used.contains(imageId)) {
				remove.accept(imageId);
				images.remove();
			}
		}
	}

	/** A map holding at most the given number of entries, dropping the least recently used. */
	static <K, V> Map<K, V> lruCache(int maxSize) {
		return new LinkedHashMap<K, V>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > maxSize;
			}
		};
	}

	/**
	 * What the pin of a POI is drawn from, so that a pin is drawn again only when it changes. A
	 * route top has a pin of its own.
	 */
	static String poiIconKey(DisplayableGeoNode poi) {
		StringBuilder styles = new StringBuilder();
		for (GeoNode.ClimbingStyle style : poi.geoNode.getClimbingStyles()) {
			styles.append(style.name()).append(',');
		}
		return poi.geoNode.osmID + "|" + poi.getAlpha() + "|" + poi.getRouteNumber()
				+ "|" + poi.geoNode.getName()
				+ "|" + poi.geoNode.getNodeType().name() + "|" + poi.geoNode.isRouteTop()
				+ "|" + styles
				+ "|" + poi.geoNode.getKey(ClimbingTags.KEY_ROUTES)
				+ "|" + poi.geoNode.routeCountFiltered
				+ "|" + poi.geoNode.getKey(ClimbingTags.KEY_LENGTH)
				+ "|" + poi.geoNode.getLevelId(ClimbingTags.KEY_GRADE_TAG);
	}

	/**
	 * Shows on the rim of the compass button how far off the compass may be, in every rotation
	 * mode, whenever the device tells.
	 */
	private void updateCompassAccuracyArc() {
		if (compassAccuracyArc != null) {
			compassAccuracyArc.setHalfWidthDegrees(
					headingAccuracyWedgeDegrees(lastHeadingAccuracyDegrees));
		}
	}

	/**
	 * Draws the observer where it is, turned to its heading, with the wedge of how far off the
	 * heading may be. The heading arrives at sensor rate, and every source update makes MapLibre
	 * parse, tile and lay the marker out again, so the marker is only sent once it would look
	 * different: moved, turned by HEADING_EPSILON_DEGREES, or with another wedge.
	 */
	private void updateObserverMarker() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		String accuracyImage =
				Double.isNaN(lastSensorHeadingDegrees) ? null : getHeadingAccuracyImage();
		boolean changed = renderedObserverLocation == null
				|| renderedObserverLocation.getLatitude() != observerLocation.getLatitude()
				|| renderedObserverLocation.getLongitude() != observerLocation.getLongitude()
				|| headingChanged(renderedObserverHeadingDegrees, lastSensorHeadingDegrees)
				|| !Objects.equals(renderedObserverAccuracyImage, accuracyImage);
		if (!changed) {
			return;
		}

		String properties = "";
		if (!Double.isNaN(lastSensorHeadingDegrees)) {
			properties = "\"" + ROTATION_PROPERTY + "\":" + lastSensorHeadingDegrees;
			if (accuracyImage != null) {
				properties += ",\"" + HEADING_ACCURACY_IMAGE_PROPERTY + "\":\"" + accuracyImage
						+ "\"";
			}
		}
		updatePointSource(OBSERVER_SOURCE_ID, observerLocation, properties);
		renderedObserverLocation = observerLocation;
		renderedObserverHeadingDegrees = lastSensorHeadingDegrees;
		renderedObserverAccuracyImage = accuracyImage;
	}

	/**
	 * Whether a heading drawn differs visibly from the heading now, so that what shows it is
	 * drawn again: turned by HEADING_EPSILON_DEGREES or more, either way round. NaN is no heading
	 * at all, which shows differently from any heading, and the same as itself.
	 */
	static boolean headingChanged(double renderedDegrees, double degrees) {
		if (Double.isNaN(renderedDegrees) || Double.isNaN(degrees)) {
			return Double.isNaN(renderedDegrees) != Double.isNaN(degrees);
		}
		return Math.abs(GeoUtils.diffAngle(degrees, renderedDegrees)) >= HEADING_EPSILON_DEGREES;
	}

	/**
	 * Whether a map following the compass is turned to a heading: once it is
	 * CAMERA_BEARING_EPSILON_DEGREES or more from the bearing the camera has, either way round.
	 * Measured from the camera, not from the last reading, so a slow turn is not lost: it adds up
	 * until the map follows.
	 */
	static boolean cameraBearingChanged(double cameraBearingDegrees, double headingDegrees) {
		return Math.abs(GeoUtils.diffAngle(headingDegrees, cameraBearingDegrees))
				>= CAMERA_BEARING_EPSILON_DEGREES;
	}

	/**
	 * The image of the wedge showing how far off the heading may be, added to the style the first
	 * time it is needed, or null when the device does not tell.
	 */
	private String getHeadingAccuracyImage() {
		int halfWidth = headingAccuracyWedgeDegrees(lastHeadingAccuracyDegrees);
		if (halfWidth == 0 || !styleLoaded || map == null || map.getStyle() == null) {
			return null;
		}
		String imageId = HEADING_ACCURACY_IMAGE_PREFIX + halfWidth;
		if (registeredHeadingAccuracyImages.add(imageId)) {
			map.getStyle().addImage(imageId, createHeadingAccuracyBitmap(halfWidth));
		}
		return imageId;
	}

	/**
	 * How wide, either side of the heading, the wedge is for a heading accuracy: rounded up to a
	 * whole step, so the wedge never looks surer than the heading is, and at most a full circle.
	 * Zero when the accuracy is unknown. The compass accuracy indicators are rounded the same.
	 */
	public static int headingAccuracyWedgeDegrees(double accuracyDegrees) {
		if (Double.isNaN(accuracyDegrees) || accuracyDegrees <= 0) {
			return 0;
		}
		int steps = (int) Math.ceil(accuracyDegrees / HEADING_ACCURACY_STEP_DEGREES);
		return Math.min(180, steps * HEADING_ACCURACY_STEP_DEGREES);
	}

	/**
	 * A wedge pointing up from the centre of the image, fading out towards its edge like the beam
	 * of a torch. The layer turns it to the heading.
	 */
	private Bitmap createHeadingAccuracyBitmap(int halfWidthDegrees) {
		float radius =
				HEADING_ACCURACY_RADIUS_DP * parent.getResources().getDisplayMetrics().density;
		int size = (int) Math.ceil(2 * radius);
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setShader(new RadialGradient(size / 2f, size / 2f, radius, HEADING_ACCURACY_COLOR,
				HEADING_ACCURACY_COLOR & 0x00ffffff, Shader.TileMode.CLAMP));
		// Arcs are measured clockwise from the right, so up is at -90 degrees.
		new Canvas(bitmap).drawArc(new RectF(0, 0, size, size), -90 - halfWidthDegrees,
				2 * halfWidthDegrees, true, paint);
		return bitmap;
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
			// This runs at sensor rate even without a cone, and every source update makes the map
			// redraw, so the sources are only emptied when they show something.
			if (renderedViewConeCenter != null) {
				coneSource.setGeoJson(EMPTY_FEATURE_COLLECTION);
				rangeSource.setGeoJson(EMPTY_FEATURE_COLLECTION);
				renderedViewConeCenter = null;
				renderedViewConeHeadingDegrees = Double.NaN;
			}
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
				|| headingChanged(renderedViewConeHeadingDegrees, lastSensorHeadingDegrees);

		if (coneChanged) {
			coneSource.setGeoJson(Double.isNaN(lastSensorHeadingDegrees)
					? EMPTY_FEATURE_COLLECTION : buildViewConeGeoJson());
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
		return ClimbingGeometryBuilder.featureCollection(Collections.singletonList(
				"{\"type\":\"Feature\",\"properties\":{},"
						+ "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[" + ring +
						"]]}}"));
	}

	private String buildViewRangeGeoJson() {
		StringBuilder ring = new StringBuilder();
		for (int segment = 0; segment <= VIEW_RANGE_CIRCLE_SEGMENTS; segment++) {
			appendObserverOffset(ring, 360.0 * segment / VIEW_RANGE_CIRCLE_SEGMENTS,
					viewConeRangeMeters);
		}
		return ClimbingGeometryBuilder.featureCollection(Collections.singletonList(
				"{\"type\":\"Feature\",\"properties\":{},"
						+ "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" + ring +
						"]}}"));
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

	/** Pins tapped on the map go to the listener instead of opening their info dialog. */
	public void setOnPoiClickListener(OnPoiClickListener listener) {
		onPoiClickListener = listener;
	}

	/** Way and relation labels tapped on the map go to the listener instead of their dialog. */
	public void setOnCollectionClickListener(OnCollectionClickListener listener) {
		onCollectionClickListener = listener;
	}

	/**
	 * Told whether the map turns with the compass, at once and then each time that changes, so
	 * that the compass can be read faster while it does.
	 */
	public void setOnFollowCompassChangeListener(OnFollowCompassChangeListener listener) {
		onFollowCompassChangeListener = listener;
		listener.onFollowCompassChange(rotationMode == RotationMode.AUTO);
	}

	public void setEditMarker(DisplayableGeoNode poi) {
		setEditMarkers(Collections.singletonList(poi));
	}

	/** Markers of what is being edited, drawn above the other pins. */
	public void setEditMarkers(List<DisplayableGeoNode> pois) {
		editMarkerPois = new ArrayList<>(pois);
		renderEditMarkers();
	}

	private void renderEditMarkers() {
		if (!styleLoaded || map == null || map.getStyle() == null) {
			return;
		}
		Style style = map.getStyle();
		for (int index = 0; index < editMarkerImageCount; index++) {
			style.removeImage(EDIT_IMAGE_ID + "-" + index);
		}
		List<String> features = new ArrayList<>();
		for (int index = 0; index < editMarkerPois.size(); index++) {
			DisplayableGeoNode poi = editMarkerPois.get(index);
			String imageId = EDIT_IMAGE_ID + "-" + index;
			Drawable drawable = new PoiMarkerDrawable(parent, poi,
					PoiMarkerDrawable.PinShape.slim).getDrawable();
			style.addImage(imageId, bitmapFromDrawable(drawable));
			features.add("{\"type\":\"Feature\",\"properties\":{\"" + ICON_PROPERTY + "\":\""
					+ imageId + "\"},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
					+ poi.geoNode.decimalLongitude + "," + poi.geoNode.decimalLatitude + "]}}");
		}
		editMarkerImageCount = editMarkerPois.size();
		GeoJsonSource source = style.getSourceAs(EDIT_SOURCE_ID);
		if (source != null) {
			source.setGeoJson(ClimbingGeometryBuilder.featureCollection(features));
		}
	}

	private void setFollowObserver(boolean enabled) {
		followObserver = enabled;
		session.followObserver = enabled;
		if (enabled) {
			// Recentering is a button move: once the camera settles, the data is loaded if the
			// view left the loaded area, see refreshForView.
			if (map == null) {
				centerOnObserver();
			} else {
				animateFromButton(CameraUpdateFactory.newLatLng(toLatLng(observerLocation)));
			}
		}
		updateLocationButton();
	}

	/**
	 * Any camera move cancels a running animation, and the sensors move the camera many times a
	 * second (the AR view rotates and recenters it continuously), so the sensor driven updates
	 * hold off until a zoom or recentering started from a button has finished.
	 */
	private void animateFromButton(CameraUpdate update) {
		if (map == null) {
			return;
		}
		automaticCameraMove = false;
		buttonAnimation = new MapLibreMap.CancelableCallback() {
			@Override
			public void onCancel() {
				onFinish();
			}

			@Override
			public void onFinish() {
				// A newer button press replaces this animation and keeps its own hold.
				if (buttonAnimation == this) {
					buttonAnimation = null;
				}
			}
		};
		map.animateCamera(update, buttonAnimation);
	}

	private void centerOnObserver() {
		if (map == null) {
			// Without a camera yet, the default one already starts on the observer.
			if (session.camera != null) {
				session.camera = new MapCameraState(observerLocation, session.camera.getZoom(),
						session.camera.getBearing());
			}
			return;
		}
		automaticCameraMove = true;
		// Following eases the camera over at its zoom. Flying there (animateCamera) zooms out on
		// the way, and as the sensors move the camera again many times a second, the next move
		// starts from that zoom and keeps it: after a long move, the map stays zoomed out. Out of
		// view, such as on the first GPS fix far from the last known location, the camera jumps
		// rather than sweep across the map.
		CameraPosition camera = map.getCameraPosition();
		LatLng target = toLatLng(observerLocation);
		CameraUpdate update = CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
				.target(target)
				.zoom(camera.zoom)
				.bearing(camera.bearing)
				.build());
		if (map.getProjection().getVisibleRegion().latLngBounds.contains(target)) {
			map.easeCamera(update);
		} else {
			map.moveCamera(update);
		}
	}

	private double currentBearing() {
		return map == null ? 0 : map.getCameraPosition().bearing;
	}

	private void rotateCamera(double bearing) {
		if (map == null) {
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		automaticCameraMove = true;
		// Each rotation cancels the animation recentering on the observer, so while following,
		// rotate around the observer instead of leaving the map behind.
		MapCoordinate target = followObserver ? observerLocation : fromLatLng(camera.target);
		moveCamera(target, camera.zoom, bearing, false);
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
		moveCamera(camera.getCenter(), camera.getZoom(), camera.getBearing(), animate);
	}

	private void saveCamera() {
		if (map == null || !styleLoaded) {
			return;
		}
		CameraPosition camera = map.getCameraPosition();
		session.camera = new MapCameraState(fromLatLng(camera.target), camera.zoom, camera.bearing);
	}

	private void updateLocationButton() {
		ImageView button = container.findViewById(R.id.mapCenterOnGpsButton);
		if (button != null) {
			if (followObserver) {
				button.clearColorFilter();
			} else {
				button.setColorFilter(BUTTON_OFF_TINT);
			}
		}
	}

	private void updateCompassButton() {
		ImageView button = container.findViewById(R.id.compassButton);
		if (button == null) {
			return;
		}
		int dial = rotationMode == RotationMode.USER ? R.drawable.ic_compass_user_dial :
				R.drawable.ic_compass_dial;
		compassBlur.setIcon(ResourcesCompat.getDrawable(parent.getResources(), dial, null),
				ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_compass_needle,
						null));
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

	/**
	 * Which map a widget is. Each profile remembers its camera, GPS following and rotation for the
	 * running session, starting from its own defaults when the app starts.
	 */
	public enum Profile {
		/**
		 * The AR minimap: fits the AR range, follows the GPS, turns with the compass and credits
		 * the terrain the AR view places POIs on.
		 */
		AR_MINIMAP(true, RotationMode.AUTO, true),
		/** The node editor: stays on the node being edited instead of following the GPS. */
		NODE_EDITOR(false, RotationMode.STATIC, false),
		/** Every other map. */
		DEFAULT(true, RotationMode.STATIC, false);

		private final boolean followObserver;
		private final RotationMode rotationMode;
		private final boolean creditTerrain;

		Profile(boolean followObserver, RotationMode rotationMode, boolean creditTerrain) {
			this.followObserver = followObserver;
			this.rotationMode = rotationMode;
			this.creditTerrain = creditTerrain;
		}
	}

	private static final class SessionState {
		// Null until the map first settles; the profile's default camera is used until then.
		private MapCameraState camera;
		private boolean followObserver;
		private RotationMode rotationMode;

		private SessionState(Profile profile) {
			followObserver = profile.followObserver;
			rotationMode = profile.rotationMode;
		}
	}

	public interface OnMapClickListener {
		void onMapClick(MapCoordinate coordinate);
	}

	public interface OnPoiClickListener {
		void onPoiClick(DisplayableGeoNode poi);
	}

	public interface OnCollectionClickListener {
		void onCollectionClick(OsmCollectionEntity collection);
	}

	public interface OnFollowCompassChangeListener {
		void onFollowCompassChange(boolean followsCompass);
	}
}
