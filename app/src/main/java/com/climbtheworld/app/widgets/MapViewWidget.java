package com.climbtheworld.app.widgets;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.openstreetmap.ui.GeoNodeMapMarker;
import com.climbtheworld.app.openstreetmap.ui.IDisplayableGeoNode;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.storage.NodeDisplayFilters;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.marker.LazyDrawable;
import com.climbtheworld.app.utils.marker.MarkerUtils;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.events.MapListener;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.util.TileSystemWebMercator;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import needle.Needle;
import needle.UiRelatedTask;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget {

    //UI Elements to scan for
    static final String MAP_CENTER_ON_GPS_BUTTON = "mapCenterOnGpsButton";
    static final String MAP_ROTATION_TOGGLE_BUTTON = "compassButton";
    static final String MAP_VIEW = "openMapView";
    static final String MAP_LAYER_TOGGLE_BUTTON = "mapLayerToggleButton";
    static final String MAP_SOURCE_NAME_TEXT_VIEW = "mapSourceName";
    static final String MAP_LOADING_INDICATOR = "mapLoadingIndicator";
    static final String IC_MY_LOCATION = "ic_my_location";

    private final Configs configs;
    private boolean mapRotationEnabled;
    private CompassWidget compass = null;

    public static final double MAP_DEFAULT_ZOOM_LEVEL = 16;
    public static final double MAP_CENTER_ON_ZOOM_LEVEL = 24;
    public static final double CLUSTER_ZOOM_LEVEL = MAP_DEFAULT_ZOOM_LEVEL - 1;

    private final List<ITileSource> tileSource = new ArrayList<>();
    private final TileSystem tileSystem = new TileSystemWebMercator();

    private final MapView osmMap;
    private final View mapContainer;
    private Marker.OnMarkerClickListener obsOnClickEvent;
    private boolean showObserver = true;
    private FolderOverlay myLocationMarkersFolder = new FolderOverlay();
    private ScaleBarOverlay scaleBarOverlay;
    private RadiusMarkerClusterer poiMarkersFolder;
    private Marker obsLocationMarker;
    private long osmLastInvalidate;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    private boolean mapAutoCenter = true;
    private FolderOverlay customMarkers;
    private AppCompatActivity parent;
    private UiRelatedTask updateTask;
    private static MapState staticState = new MapState();
    private MapMarkerClusterClickListener clusterClick = null;

    private static final int MAP_REFRESH_INTERVAL_MS = 100;

    private static final Semaphore refreshLock = new Semaphore(1);
    private boolean forceUpdate = false;

    public void setClearState(boolean cleanState) {
        forceUpdate = cleanState;
    }

    public interface MapMarkerClusterClickListener {
        void onClusterCLick(StaticCluster cluster);
    }

    private static class MapState {
        IGeoPoint center = Globals.poiToGeoPoint(Globals.virtualCamera);
        double zoom = MapViewWidget.MAP_DEFAULT_ZOOM_LEVEL;
        boolean centerOnObs = true;
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

    public MapViewWidget(AppCompatActivity pActivity, View pOsmMap, boolean useVirtualCamera) {
        this(pActivity, pOsmMap, useVirtualCamera, null);
    }

    public MapViewWidget(AppCompatActivity pActivity, View pOsmMap, boolean useVirtualCamera, FolderOverlay pCustomMarkers) {
        this.parent = pActivity;
        this.mapContainer = pOsmMap;
        this.osmMap = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_VIEW, "id", parent.getPackageName()));
        poiMarkersFolder = createClusterMarker();

        configs = Configs.instance(parent);

        this.customMarkers = pCustomMarkers;
        if (useVirtualCamera) {
            staticState.center = Globals.poiToGeoPoint(Globals.virtualCamera);
            staticState.zoom = MapViewWidget.MAP_DEFAULT_ZOOM_LEVEL;
            staticState.centerOnObs = true;
        }

        scaleBarOverlay = new ScaleBarOverlay(osmMap);
        scaleBarOverlay.setAlignBottom(true);
        scaleBarOverlay.setAlignRight(true);
        scaleBarOverlay.setEnableAdjustLength(true);

        osmMap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                boolean eventCaptured = false;
                if ((motionEvent.getAction() == MotionEvent.ACTION_MOVE)) {
                    setMapAutoFollow(false);
                }

                for (View.OnTouchListener listener : touchListeners) {
                    eventCaptured = eventCaptured || listener.onTouch(view, motionEvent);
                }

                return eventCaptured;
            }
        });

        initMapPointers();

        osmMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(staticState.zoom);
        osmMap.setScrollableAreaLimitLatitude(tileSystem.getMaxLatitude() - 0.1, -tileSystem.getMaxLatitude() + 0.1, 0);

        osmMap.getController().setCenter(staticState.center);

        osmMap.post(new Runnable() {
            @Override
            public void run() {
                osmMap.setMinZoomLevel(tileSystem.getLatitudeZoom(tileSystem.getMaxLatitude(), -tileSystem.getMaxLatitude(), mapContainer.getHeight()));
            }
        });

        setShowObserver(this.showObserver, null);

        setMapButtonListener();
        setMapAutoFollow(staticState.centerOnObs);
        setCopyright();
    }

    public void resetZoom() {
        osmMap.getController().setZoom(MapViewWidget.MAP_DEFAULT_ZOOM_LEVEL);
    }

    private void initMapPointers() {
        osmMap.getOverlays().clear();
        if (customMarkers != null) {
            osmMap.getOverlays().add(customMarkers);
        }

        osmMap.getOverlays().add(scaleBarOverlay);
        osmMap.getOverlays().add(myLocationMarkersFolder);
    }

    public void addMapListener(MapListener listener) {
        osmMap.addMapListener(listener);
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

        button = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_CENTER_ON_GPS_BUTTON, "id", parent.getPackageName()));
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (view.getTag() != "on") {
                        setMapAutoFollow(true);
                    } else {
                        setMapAutoFollow(false);
                    }
                }
            });
        }

        button = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", parent.getPackageName()));
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (view.getTag() != "on") {
                        setRotationMode(true);
                    } else {
                        setRotationMode(false);
                    }
                }
            });

            compass = new CompassWidget(button);
        }
    }

    public MapView getOsmMap() {
        return osmMap;
    }

    public double getMaxZoomLevel() {
        return (double) getOsmMap().getTileProvider().getTileSource().getMaximumZoomLevel();
    }

    public void addTouchListener(View.OnTouchListener listener) {
        this.touchListeners.add(listener);
    }

    public void setRotationMode(boolean enable) {
        obsLocationMarker.setRotation(0f);
        osmMap.setMapOrientation(0f);

        if (enable) {
            mapRotationEnabled = true;
            invalidate();
        } else {
            mapRotationEnabled = false;
        }
        updateRotationButton(enable);
        configs.setBoolean(Configs.ConfigKey.mapViewCompassOrientation, mapRotationEnabled);
    }

    private void updateRotationButton(boolean enable) {
        ImageView img = parent.findViewById(parent.getResources().getIdentifier(MAP_ROTATION_TOGGLE_BUTTON, "id", parent.getPackageName()));
        if (img != null) {
            if (enable) {
                img.setTag("on");
            } else {
                img.setTag("");
            }
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

    private void centerOnObserver() {
        centerOnGoePoint(obsLocationMarker.getPosition());
    }

    public void centerOnGoePoint(GeoPoint location) {
        centerOnGoePoint(location, osmMap.getZoomLevelDouble());
    }

    public void centerOnGoePoint(GeoPoint location, Double zoom) {
        osmMap.getController().animateTo(location, zoom, null);
        osmMap.setExpectedCenter(location);
    }

    public void setMapAutoFollow(boolean enable) {
        if (enable) {
            mapAutoCenter = true;
            centerOnObserver();
            invalidate();
        } else {
            mapAutoCenter = false;
        }
        updateAutoFollowButton(enable);
    }

    private void updateAutoFollowButton(boolean enable) {
        ImageView img = parent.findViewById(parent.getResources().getIdentifier(MAP_CENTER_ON_GPS_BUTTON, "id", parent.getPackageName()));
        if (img != null) {
            if (enable) {
                img.setColorFilter(null);
                img.setTag("on");
            } else {
                img.setColorFilter(Color.argb(250, 200, 200, 200));
                img.setTag("");
            }
        }
    }

    public void setClusterOnClickListener(MapMarkerClusterClickListener listener) {
        this.clusterClick = listener;
    }

    public void setMapTileSource(ITileSource tileSource) {
        osmMap.setTileSource(tileSource);
        invalidate();
    }

    public void resetPOIs(final List<? extends IDisplayableGeoNode> poiList) {
        resetPOIs(poiList, true);
    }

    public void resetPOIs(final List<? extends IDisplayableGeoNode> globalPoiList, final boolean withFilters) {
        if (updateTask != null) {
            updateTask.cancel();
        }

        final List<IDisplayableGeoNode> poiList = new ArrayList<>(globalPoiList);

        final View loadStatus = mapContainer.findViewById(parent.getResources().getIdentifier(MAP_LOADING_INDICATOR, "id", parent.getPackageName()));
        if (loadStatus != null) {
            loadStatus.setVisibility(View.VISIBLE);
        }

        updateTask = new UiRelatedTask() {
            @Override
            protected Object doWork() {
                try {
                    refreshLock.acquire();
                    ArrayList<Marker> markerList = poiMarkersFolder.getItems();

                    //if forced reset all markers
//                    if (forceUpdate) {
//                        markerList.clear();
//                        forceUpdate = false;
//                    }

                    Iterator<Marker> deleteIterator = markerList.iterator();
                    while (deleteIterator.hasNext()) {
                        if (isCanceled()) {
                            return null;
                        }
                        Marker marker = deleteIterator.next();
                        boolean found = false;

                        Iterator<? extends IDisplayableGeoNode> newIterator = poiList.iterator();
                        while (newIterator.hasNext()) {
                            if (isCanceled()) {
                                return null;
                            }

                            IDisplayableGeoNode refreshPOI = newIterator.next();
                            if (marker.getPosition().toDoubleString().equals(Globals.poiToGeoPoint(refreshPOI.getGeoNode()).toDoubleString())) {
                                if (Math.floor(osmMap.getZoomLevelDouble()) <= CLUSTER_ZOOM_LEVEL) {
                                    if (refreshPOI.isVisible()) {
                                        found = true;
                                        newIterator.remove();
                                    }
                                } else {
                                    found = true;
                                    newIterator.remove();
                                }
                                break;
                            }
                        }

                        if (!found) {
                            deleteIterator.remove();
                        }
                    }

                    //add nodes that are missing.
                    for (IDisplayableGeoNode refreshPOI : poiList) {
                        if (isCanceled()) {
                            return null;
                        }

                        if (withFilters) {
                            refreshPOI.setGhost(NodeDisplayFilters.passFilter(Configs.instance(parent), refreshPOI.getGeoNode()));
                        }

                        Marker poiMarker = buildMapMarker(refreshPOI);

                        if (Math.floor(osmMap.getZoomLevelDouble()) > CLUSTER_ZOOM_LEVEL) {
                            if (!markerList.contains(poiMarker)) {
                                markerList.add(poiMarker);
                            }
                        } else if (!refreshPOI.isVisible()) {
                            markerList.remove(poiMarker);
                        } else if (!markerList.contains(poiMarker)) {
                            markerList.add(poiMarker);
                        }
                    }
                } catch (NullPointerException | InterruptedException e) { //buildMapMarker may generate null pointer if view is terminated in the middle of execution.
                    e.printStackTrace();
                    return null;
                } finally {
                    refreshLock.release();
                }

                return null;
            }

            @Override
            protected void thenDoUiRelatedWork(Object o) {
                invalidate();
                if (loadStatus != null) {
                    loadStatus.setVisibility(View.GONE);
                }
            }
        };

        Needle.onBackgroundThread()
                .withTaskType("ClusterTask")
                .withThreadPoolSize(1)
                .execute(updateTask);
    }

    private void refreshMarkers() {
        if (refreshLock.tryAcquire()) {
            if (Math.floor(osmMap.getZoomLevelDouble()) > CLUSTER_ZOOM_LEVEL) {
                Collections.sort(poiMarkersFolder.getItems(), new Comparator<Marker>() {
                    Point tempPoint1 = new Point();
                    Point tempPoint2 = new Point();
                    Projection pj = osmMap.getProjection();

                    @Override
                    public int compare(Marker element1, Marker element2) {
                        pj.toPixels(element1.getPosition(), tempPoint1);
                        pj.rotateAndScalePoint(tempPoint1.x, tempPoint1.y, tempPoint1);

                        pj.toPixels(element2.getPosition(), tempPoint2);
                        pj.rotateAndScalePoint(tempPoint2.x, tempPoint2.y, tempPoint2);

                        return Double.compare(tempPoint1.y, tempPoint2.y);
                    }
                });
            }

            if (!osmMap.getOverlays().contains(poiMarkersFolder)) {
                osmMap.getOverlays().add(poiMarkersFolder);
            }

            poiMarkersFolder.invalidate();
            refreshLock.release();
        }
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

    private RadiusMarkerClusterer createClusterMarker() {
        RadiusMarkerClusterer result = new RadiusMarkerWithClickEvent(osmMap.getContext());
        result.setMaxClusteringZoomLevel((int) CLUSTER_ZOOM_LEVEL);
        BitmapDrawable clusterIcon = (BitmapDrawable) MarkerUtils.getClusterIcon(parent, DisplayableGeoNode.CLUSTER_DEFAULT_COLOR, 255);
        if (clusterIcon != null) {
            Bitmap icon = clusterIcon.getBitmap();
            result.setRadius(Math.max(icon.getHeight(), icon.getWidth()));
            result.setIcon(icon);
        }

        return result;
    }

    private Marker buildMapMarker(final IDisplayableGeoNode poi) {
        Drawable nodeIcon = poi.getIcon(parent);
        ((LazyDrawable) nodeIcon).setMapWidget(this);

        Marker nodeMarker = new GeoNodeMapMarker(osmMap, poi.getMarkerData());
        nodeMarker.setAnchor(((LazyDrawable) nodeIcon).getAnchorU(), ((LazyDrawable) nodeIcon).getAnchorV());
        nodeMarker.setPosition(Globals.poiToGeoPoint(poi.getGeoNode()));
        nodeMarker.setIcon(nodeIcon);

        if (poi.isShowPoiInfoDialog()) {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    poi.showOnClickDialog(parent);
                    return true;
                }
            });
        } else {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    return false;
                }
            });
        }
        return nodeMarker;
    }

    private void initMyLocationMarkers() {
        List<Overlay> list = myLocationMarkersFolder.getItems();

        list.clear();

        Drawable nodeIcon = ResourcesCompat.getDrawable(osmMap.getContext().getResources(), parent.getResources().getIdentifier(IC_MY_LOCATION, "drawable", parent.getPackageName()), null);

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
        obsLocationMarker.getPosition().setCoords(location.getLatitude(), location.getLongitude());
        obsLocationMarker.getPosition().setAltitude(location.getAltitude());

        if (mapAutoCenter) {
            centerOnObserver();
        }
    }

    public void onOrientationChange(OrientationManager.OrientationEvent event) {
        if (mapRotationEnabled) {
            osmMap.setMapOrientation(-(float) event.getAdjusted().x, true);
            if (compass != null) {
                compass.updateOrientation(event);
            }
            refreshMarkers();
        } else {
            obsLocationMarker.setRotation(-(float) event.getAdjusted().x);
            if (compass != null) {
                compass.updateOrientation(new OrientationManager.OrientationEvent());
            }
        }
    }

    public void invalidate() {
        if (System.currentTimeMillis() - osmLastInvalidate < MAP_REFRESH_INTERVAL_MS) {
            return;
        }
        osmLastInvalidate = System.currentTimeMillis();
        refreshMarkers();

        osmMap.invalidate();
    }

    public void onPause() {
        osmMap.onPause();
        staticState.center = osmMap.getMapCenter();
        staticState.zoom = osmMap.getZoomLevelDouble();
        staticState.centerOnObs = mapAutoCenter;
    }

    public void onResume() {
        osmMap.onResume();
    }
}
