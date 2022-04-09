package com.climbtheworld.app.map.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.GeoNodeMapMarker;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.util.TileSystemWebMercator;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget {
	//UI Elements to scan for
	static final String MAP_VIEW = "openMapView";
	static final String MAP_LAYER_TOGGLE_BUTTON = "mapLayerToggleButton";
	static final String MAP_SOURCE_NAME_TEXT_VIEW = "mapSourceName";
	static final String MAP_LOADING_INDICATOR = "mapLoadingIndicator";
	private static final long MAP_EVENT_DELAY_MS = 500;
	private static final int MAP_EVENT_DELAY_MAX_DROP = 20;

	final Configs configs;
	private final View loadStatus;
	private final DataManager downloadManager;

	public static final double MAP_DEFAULT_ZOOM_LEVEL = 16;
	public static final double MAP_CENTER_ON_ZOOM_LEVEL = 24;
	public static final double CLUSTER_ZOOM_LEVEL = MAP_DEFAULT_ZOOM_LEVEL - 1;
	static MapState staticState = new MapState();

	private final List<ITileSource> tileSource = new ArrayList<>();
	private final TileSystem tileSystem = new TileSystemWebMercator();

	final MapView osmMap;
	final View mapContainer;
	private Marker.OnMarkerClickListener obsOnClickEvent;
	private boolean showObserver = true;
	private final FolderOverlay myLocationMarkersFolder = new FolderOverlay();
	private final ScaleBarOverlay scaleBarOverlay;
	private final RadiusMarkerClusterer poiMarkersFolder;
	Marker obsLocationMarker;
	private long osmLastInvalidate;
	private final List<View.OnTouchListener> touchListeners = new ArrayList<>();

	private FolderOverlay customMarkers;
	AppCompatActivity parent;
	private UiRelatedTask<Boolean> updateTask;
	private MapMarkerClusterClickListener clusterClick = null;
	private MinimapOverlay minimap = null;

	private static final Semaphore refreshLock = new Semaphore(1);
	private boolean forceUpdate = true;

	private final Map<Long, DisplayableGeoNode> visiblePOIs = new ConcurrentHashMap<>();
	private FilterType filterMethod = FilterType.USER;
	private final Map<String, ButtonMapWidget> activeWidgets = new HashMap<>();

	static class MapState {
		public IGeoPoint center = Globals.geoNodeToGeoPoint(Globals.virtualCamera);
		public double zoom = MapViewWidget.MAP_DEFAULT_ZOOM_LEVEL;
		public boolean mapFollowObserver = true;
	}

	public void addCustomOverlay(Overlay customOverlay) {
		if (!osmMap.getOverlays().contains(customOverlay)) {
			osmMap.getOverlays().add(customOverlay);
		}
	}

	public void removeCustomOverlay(Overlay customOverlay) {
		osmMap.getOverlays().remove(customOverlay);
	}

	public enum FilterType {
		NONE, USER, GHOSTS
	}

	public void setFilterMethod(FilterType method) {
		filterMethod = method;
	}

	public void setClearState(boolean cleanState) {
		forceUpdate = cleanState;
	}

	public void setTapMarker(FolderOverlay tapMarkersFolder) {
		this.customMarkers = tapMarkersFolder;
		initMapPointers();
	}

	public interface MapMarkerClusterClickListener {
		void onClusterCLick(StaticCluster cluster);
	}

	class RadiusMarkerWithClickEvent extends RadiusMarkerClusterer {

		public RadiusMarkerWithClickEvent(Context ctx) {
			super(ctx);
		}

		@Override
		public Marker buildClusterMarker(final StaticCluster cluster, MapView mapView) {
			Marker m = super.buildClusterMarker(cluster, mapView);
			m.setRelatedObject(cluster);
			m.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
				@Override
				public boolean onMarkerClick(Marker marker, MapView mapView) {
					if (clusterClick != null) {
						clusterClick.onClusterCLick(cluster);
					}
					return false;
				}
			});
			return m;
		}
	}

	public MapViewWidget(AppCompatActivity parent, View mapContainerView, boolean startAtVirtualCamera) {
		this.parent = parent;
		this.mapContainer = mapContainerView;
		this.downloadManager = new DataManager(parent);
		this.osmMap = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_VIEW, "id", parent.getPackageName()));
		this.loadStatus = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_LOADING_INDICATOR, "id", parent.getPackageName()));
		this.poiMarkersFolder = createClusterMarker();

		configs = Configs.instance(parent);

		if (startAtVirtualCamera) {
			staticState.center = Globals.geoNodeToGeoPoint(Globals.virtualCamera);
			staticState.zoom = MapViewWidget.MAP_DEFAULT_ZOOM_LEVEL;
			staticState.mapFollowObserver = true;
		}

		scaleBarOverlay = new ScaleBarOverlay(osmMap);
		scaleBarOverlay.setAlignBottom(true);
		scaleBarOverlay.setAlignRight(true);
		scaleBarOverlay.setEnableAdjustLength(true);

		initMapPointers();
		initMapWidgetEvents();

		osmMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
		osmMap.setTilesScaledToDpi(true);
		osmMap.setMultiTouchControls(true);
		osmMap.setVerticalMapRepetitionEnabled(false);
		osmMap.getController().setZoom(staticState.zoom);
		osmMap.setScrollableAreaLimitLatitude(tileSystem.getMaxLatitude() - 0.1, -tileSystem.getMaxLatitude() + 0.1, 0);

		osmMap.getController().setCenter(staticState.center);

		osmMap.addOnFirstLayoutListener(new MapView.OnFirstLayoutListener() {
			@Override
			public void onFirstLayout(View v, int left, int top, int right, int bottom) {
				osmMap.setMinZoomLevel(tileSystem.getLatitudeZoom(tileSystem.getMaxLatitude(), -tileSystem.getMaxLatitude(), mapContainer.getHeight()));
			}
		});

		setShowObserver(this.showObserver, null);

		setMapButtonListener();
		setMapAutoFollow(staticState.mapFollowObserver);
		setCopyright();
	}

	private void initMapWidgetEvents() {
		osmMap.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				boolean eventCaptured = false;
				for (ButtonMapWidget widget : activeWidgets.values()) {
					widget.onTouch(motionEvent);
				}

				for (View.OnTouchListener listener : touchListeners) {
					eventCaptured = eventCaptured || listener.onTouch(view, motionEvent);
				}

				return eventCaptured;
			}
		});

		osmMap.addMapListener(new MapListener() {
			@Override
			public boolean onScroll(ScrollEvent event) {
				return false;
			}

			@Override
			public boolean onZoom(ZoomEvent event) {
				invalidate(false);
				return false;
			}
		});
	}

	public void setMinimap(boolean enable, int zoomDiff) {
		removeCustomOverlay(minimap);
		if (enable) {
			minimap = new MinimapOverlay(parent, osmMap.getTileRequestCompleteHandler());
			minimap.setTileSource(tileSource.get(0));
			if (zoomDiff > 0) {
				minimap.setZoomDifference(zoomDiff);
			}
			addCustomOverlay(minimap);
		}
	}

	public void enableAutoLoad() {
		osmMap.addMapListener(new ExtendedDelayedMapListener(new MapListener() {
			@Override
			public boolean onScroll(ScrollEvent event) {
				downloadPOIs(true);
				return false;
			}

			@Override
			public boolean onZoom(ZoomEvent event) {
				downloadPOIs(true);
				return false;
			}
		}, MAP_EVENT_DELAY_MS, MAP_EVENT_DELAY_MAX_DROP));
	}

	public void setZoom(double zoom) {
		osmMap.getController().setZoom(zoom);
	}

	private void initMapPointers() {
		osmMap.getOverlays().clear();
		osmMap.getOverlays().add(myLocationMarkersFolder);

		if (customMarkers != null) {
			osmMap.getOverlays().add(customMarkers);
		}

		osmMap.getOverlays().add(scaleBarOverlay);
		osmMap.getOverlays().add(poiMarkersFolder);
	}

	public void setTileSource(ITileSource... pTileSource) {
		this.tileSource.clear();
		this.tileSource.addAll(Arrays.asList(pTileSource));
		int selectedTile = 0;
		selectedTile = configs.getInt(Configs.ConfigKey.mapViewTileOrder) % this.tileSource.size();
		setMapTileSource(this.tileSource.get(selectedTile));
	}

	private void setMapButtonListener() {
		View button = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_LAYER_TOGGLE_BUTTON, "id", parent.getPackageName()));
		if (button != null) {
			button.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					flipTileProvider(false);
				}
			});
		}

		LocationButtonMapWidget.addToActiveWidgets(this, activeWidgets);
		CompassButtonMapWidget.addToActiveWidgets(this, activeWidgets);
	}

	public MapView getOsmMap() {
		return osmMap;
	}

	public double getMaxZoomLevel() {
		return getOsmMap().getTileProvider().getTileSource().getMaximumZoomLevel();
	}

	public void addTouchListener(View.OnTouchListener listener) {
		this.touchListeners.add(listener);
	}

	public void setRotationMode(int mode) {
		if (activeWidgets.containsKey(CompassButtonMapWidget.KEY_NAME)) {
			((CompassButtonMapWidget) activeWidgets.get(CompassButtonMapWidget.KEY_NAME)).setAutoRotationMode(CompassButtonMapWidget.RotationMode.values()[mode]);
		}
	}

	public void flipTileProvider(boolean resetZoom) {
		if (tileSource.size() == 0) {
			return;
		}

		ITileSource tilesProvider = getOsmMap().getTileProvider().getTileSource();
		int tileSelected = (tileSource.indexOf(tilesProvider) + 1) % tileSource.size();
		tilesProvider = tileSource.get(tileSelected);

		configs.setInt(Configs.ConfigKey.mapViewTileOrder, tileSelected);

		double providerMaxZoom = getMaxZoomLevel();
		if ((getOsmMap().getZoomLevelDouble() > providerMaxZoom) && (resetZoom)) {
			getOsmMap().getController().setZoom(providerMaxZoom);
		}

		setMapTileSource(tilesProvider);
	}

	public void setShowObserver(boolean enable, Marker.OnMarkerClickListener obsOnTouchEvent) {
		this.obsOnClickEvent = obsOnTouchEvent;
		this.showObserver = enable;

		if (this.showObserver) {
			initMyLocationMarkers();
		} else {
			osmMap.getOverlays().remove(myLocationMarkersFolder);
		}
	}

	public void setUseDataConnection(boolean enabled) {
		osmMap.setUseDataConnection(enabled);
	}

	public void centerOnGoePoint(GeoPoint location) {
		centerOnGoePoint(location, osmMap.getZoomLevelDouble());
	}

	public void centerOnGoePoint(GeoPoint location, Double zoom) {
		osmMap.getController().animateTo(location, zoom, null);
		osmMap.setExpectedCenter(location);
	}

	public void setMapAutoFollow(boolean enable) {
		if (activeWidgets.containsKey(LocationButtonMapWidget.keyName)) {
			((LocationButtonMapWidget) activeWidgets.get(LocationButtonMapWidget.keyName)).setMapAutoFollow(enable);
		}
	}

	public void setClusterOnClickListener(MapMarkerClusterClickListener listener) {
		this.clusterClick = listener;
	}

	public void setMapTileSource(ITileSource tileSource) {
		osmMap.setTileSource(tileSource);
	}

	private void setCopyright() {
		TextView nameText = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_SOURCE_NAME_TEXT_VIEW, "id", parent.getPackageName()));
		nameText.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"));
				parent.startActivity(browserIntent);
			}
		});
	}

	public RadiusMarkerClusterer createClusterMarker() {
		RadiusMarkerClusterer result = new RadiusMarkerWithClickEvent(parent);
		result.setMaxClusteringZoomLevel((int) CLUSTER_ZOOM_LEVEL);
		BitmapDrawable clusterIcon = (BitmapDrawable) MarkerUtils.getClusterIcon(parent, DisplayableGeoNode.CLUSTER_DEFAULT_COLOR, 255);
		if (clusterIcon != null) {
			Bitmap icon = clusterIcon.getBitmap();
			result.setRadius(Math.max(icon.getHeight(), icon.getWidth()));
			result.setIcon(icon);
		}

		return result;
	}

	private void initMyLocationMarkers() {
		List<Overlay> list = myLocationMarkersFolder.getItems();

		list.clear();

		Drawable nodeIcon = ResourcesCompat.getDrawable(osmMap.getContext().getResources(), R.drawable.ic_my_location, null);

		obsLocationMarker = new Marker(osmMap);
		obsLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
		obsLocationMarker.setIcon(nodeIcon);
		obsLocationMarker.setImage(nodeIcon);
		obsLocationMarker.setInfoWindow(null);

		obsLocationMarker.setOnMarkerClickListener(obsOnClickEvent);

		//put into FolderOverlay list
		list.add(obsLocationMarker);

		myLocationMarkersFolder.closeAllInfoWindows();
	}

	public void onLocationChange(GeoPoint location) {
		for (ButtonMapWidget widget : activeWidgets.values()) {
			widget.onLocationChange(location);
		}
	}

	public void onOrientationChange(Vector4d event) {
		for (ButtonMapWidget widget : activeWidgets.values()) {
			widget.onOrientationChange(event);
		}
	}

	public void invalidateData() {
		downloadPOIs(false);
	}

	public void invalidate(boolean force) {
		if (!force && (System.currentTimeMillis() - osmLastInvalidate < Constants.TIME_TO_FRAME_MS)) {
			return;
		}
		osmLastInvalidate = System.currentTimeMillis();
		zIndexMarkers();
		osmMap.invalidate();
	}

	public void onPause() {
		osmMap.onPause();
		staticState.center = osmMap.getMapCenter();
		staticState.zoom = osmMap.getZoomLevelDouble();
	}

	public void onResume() {
		osmMap.onResume();
	}

	protected void resetMapProjection() {
		osmMap.setExpectedCenter(osmMap.getProjection().getCurrentCenter());
	}

	private void downloadPOIs(boolean cancelable) {
		if (!cancelable && osmMap.isAnimating()) {
			return;
		}

		updateLoading(View.VISIBLE);

		if (updateTask != null) {
			updateTask.cancel();
		}

		final BoundingBox bBox = osmMap.getProjection().getBoundingBox();
		updateTask = new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				if (cancelable && isCanceled()) {
					return false;
				}
				visiblePOIs.clear();

				boolean result = downloadManager.loadBBox(bBox, visiblePOIs);
				if (result || visiblePOIs.isEmpty() && !isCanceled()) {
					return refreshPOIs(this, new ArrayList<DisplayableGeoNode>(visiblePOIs.values()), cancelable);
				} else {
					return false;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean completed) {
				if (completed) {
					invalidate(!cancelable);
				}

				updateLoading(View.GONE);
			}
		};

		Constants.MAP_EXECUTOR
				.execute(updateTask);
	}

	private void updateLoading(int gone) {
		if (loadStatus != null) {
			loadStatus.post(new Runnable() {
				@Override
				public void run() {
					loadStatus.setVisibility(gone);
				}
			});
		}
	}

	private boolean refreshPOIs(UiRelatedTask<Boolean> runner, final List<? extends DisplayableGeoNode> poiList, boolean cancelable) {
		try {
			refreshLock.acquireUninterruptibly();
			ArrayList<Marker> markerList = poiMarkersFolder.getItems();

			//if forced reset all markers
			if (forceUpdate) {
				markerList.clear();
				forceUpdate = false;
			}

			Iterator<Marker> markerPOIsIterator = markerList.iterator();
			while (markerPOIsIterator.hasNext()) {
				if (cancelable && runner.isCanceled()) {
					return false;
				}
				GeoNodeMapMarker marker = (GeoNodeMapMarker) markerPOIsIterator.next();
				boolean found = false;

				Iterator<? extends DisplayableGeoNode> geoPOIIterator = poiList.iterator();
				while (geoPOIIterator.hasNext()) {
					if (cancelable && runner.isCanceled()) {
						return false;
					}

					DisplayableGeoNode refreshPOI = geoPOIIterator.next();
					if (marker.getPosition().toDoubleString().equals(Globals.geoNodeToGeoPoint(refreshPOI.getGeoNode()).toDoubleString())) {
						found = true;
						geoPOIIterator.remove();
						break;
					}
				}

				if (!found) {
					markerPOIsIterator.remove();
				} else if (filterMethod == FilterType.USER || filterMethod == FilterType.GHOSTS) {
					if (filterMethod == FilterType.USER) {
						marker.applyFilters();
					} else {
						marker.setGhost(true);
					}

					if (Math.floor(osmMap.getZoomLevelDouble()) <= CLUSTER_ZOOM_LEVEL && !marker.getPoi().isVisible()) {
						markerPOIsIterator.remove();
					}
				}
			}

			//add nodes that are missing.
			for (DisplayableGeoNode refreshPOI : poiList) {
				if (cancelable && runner.isCanceled()) {
					return false;
				}

				GeoNodeMapMarker poiMarker = new GeoNodeMapMarker(parent, osmMap, refreshPOI);
				if (filterMethod == FilterType.USER) {
					poiMarker.applyFilters();
				} else {
					poiMarker.setGhost(filterMethod == FilterType.GHOSTS);
				}

				if (Math.floor(osmMap.getZoomLevelDouble()) > CLUSTER_ZOOM_LEVEL) {
					markerList.add(poiMarker);
				} else if (poiMarker.getPoi().isVisible()) {
					markerList.add(poiMarker);
				}
			}
		} catch (NullPointerException e) { //buildMapMarker may generate null pointer if view is terminated in the middle of execution.
			e.printStackTrace();
			return false;
		} finally {
			refreshLock.release();
		}

		return !cancelable || !runner.isCanceled();
	}

	private void zIndexMarkers() {
		if (refreshLock.tryAcquire()) {
			if (Math.floor(osmMap.getZoomLevelDouble()) > CLUSTER_ZOOM_LEVEL) {
				Collections.sort(poiMarkersFolder.getItems(), new Comparator<Marker>() {
					final Point tempPoint1 = new Point();
					final Point tempPoint2 = new Point();
					final Projection projection = osmMap.getProjection();

					@Override
					public int compare(Marker element1, Marker element2) {
						projection.toPixels(element1.getPosition(), tempPoint1);
						projection.rotateAndScalePoint(tempPoint1.x, tempPoint1.y, tempPoint1);

						projection.toPixels(element2.getPosition(), tempPoint2);
						projection.rotateAndScalePoint(tempPoint2.x, tempPoint2.y, tempPoint2);

						return Double.compare(tempPoint1.y, tempPoint2.y);
					}
				});
			}
			poiMarkersFolder.invalidate();
			refreshLock.release();
		}
	}
}
