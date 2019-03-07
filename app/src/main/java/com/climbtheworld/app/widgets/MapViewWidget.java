package com.climbtheworld.app.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.events.MapListener;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.MapQuestTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.util.TileSystemWebMercator;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import needle.UiRelatedTask;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget implements View.OnClickListener {

    public interface MapMarkerElement {
        GeoPoint getGeoPoint();
        Drawable getIcon(AppCompatActivity parent);
        int getOverlayPriority();
        Drawable getOverlayIcon(AppCompatActivity parent);
        void showOnClickDialog(AppCompatActivity parent);
        Object getMarkerData();
    }

    final ITileSource mapBoxTileSource;
    final TileSystem tileSystem = new TileSystemWebMercator();

    private final MapView osmMap;
    private final View mapContainer;
    private Marker.OnMarkerClickListener obsOnClickEvent;
    private boolean showObserver = true;
    private FolderOverlay myLocationMarkersFolder = new FolderOverlay();
    private ScaleBarOverlay scaleBarOverlay;
    private SortedMap<Integer, RadiusMarkerClusterer> poiMarkersFolder = new TreeMap<>();
    private Marker obsLocationMarker;
    private long osmLastInvalidate;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    private Map<Long, ? extends MapMarkerElement> poiList; //database
    private boolean showPoiInfoDialog = true;
    private boolean mapAutoCenter = true;
    private FolderOverlay customMarkers;
    private AppCompatActivity parent;
    private UiRelatedTask updateTask;
    private GeoPoint deviceLocation;

    private static final int MAP_REFRESH_INTERVAL_MS = 100;

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
                    NodeDialogBuilder.showClusterDialog(parent, cluster);
                    return false;
                }
            });
            return m;
        }
    }

    public class GeoNodeMapMarker extends Marker {
        private Object poi;
        public GeoNodeMapMarker(MapView mapView, Object poi) {
            super(mapView);
            this.poi = poi;
        }

        public Object getGeoNode() {
            return poi;
        }
    }

    public MapViewWidget(AppCompatActivity pActivity,View pOsmMap, Map<Long, ? extends MapMarkerElement> poiDB, GeoPoint center) {
        this(pActivity, pOsmMap, poiDB, center, null);
    }

    public MapViewWidget(AppCompatActivity pActivity, View pOsmMap, Map<Long, ? extends MapMarkerElement> poiDB, GeoPoint center, FolderOverlay pCustomMarkers) {
        this.parent = pActivity;
        this.mapContainer = pOsmMap;
        this.osmMap = mapContainer.findViewById(R.id.openMapView);
        this.poiList = poiDB;
        this.customMarkers = pCustomMarkers;
        this.deviceLocation = center;

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

                for (View.OnTouchListener listener: touchListeners) {
                    eventCaptured = eventCaptured || listener.onTouch(view, motionEvent);
                }

                return eventCaptured;
            }
        });

        initMapPointers();

        osmMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(Constants.MAP_DEFAUL_ZOOM_LEVEL);
        osmMap.setScrollableAreaLimitLatitude(tileSystem.getMaxLatitude() - 0.1,-tileSystem.getMaxLatitude() + 0.1, 0);

        osmMap.getController().setCenter(deviceLocation);

        osmMap.post(new Runnable() {
            @Override
            public void run() {
                setMapTileSource(TileSourceFactory.OpenTopo);
                osmMap.setMinZoomLevel(tileSystem.getLatitudeZoom(tileSystem.getMaxLatitude(),-tileSystem.getMaxLatitude(), mapContainer.getHeight()));
            }
        });

        resetPOIs();
        setShowObserver(this.showObserver, null);

        mapBoxTileSource = new MapQuestTileSource(parent);

        setMapButtonListener();
        setMapAutoFollow(false);
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

    private void setMapButtonListener() {
        View button = mapContainer.findViewById(R.id.mapLayerToggleButton);
        if (button != null) {
            button.setOnClickListener(this);
        }

        button = mapContainer.findViewById(R.id.mapCenterOnGpsButton);
        if (button != null) {
            button.setOnClickListener(this);
        }
    }

    public MapView getOsmMap() {
        return osmMap;
    }

    public double getMaxZoomLevel() {
        return (double) getOsmMap().getTileProvider().getTileSource().getMaximumZoomLevel();
    }

    public void addTouchListener (View.OnTouchListener listener) {
        this.touchListeners.add(listener);
    }

    public void flipLayerProvider (boolean resetZoom) {
        ITileSource tilesProvider = getOsmMap().getTileProvider().getTileSource();

        if (tilesProvider.equals(TileSourceFactory.OpenTopo)) {
            tilesProvider = TileSourceFactory.MAPNIK;
        } else if (tilesProvider.equals(TileSourceFactory.MAPNIK)) {
            tilesProvider = mapBoxTileSource;
        } else if (tilesProvider.equals(mapBoxTileSource)) {
            tilesProvider = TileSourceFactory.OpenTopo;
        }

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

    public void setShowPoiInfoDialog (boolean enable) {
        this.showPoiInfoDialog = enable;
    }

    private void centerOnObserver() {
        centerOnGoePoint(deviceLocation);
    }

    public void centerOnGoePoint(GeoPoint location) {
        centerOnGoePoint(location, osmMap.getZoomLevelDouble());
    }

    public void centerOnGoePoint(GeoPoint location, Double zoom) {
        osmMap.getController().animateTo(location, zoom, null);
        osmMap.setExpectedCenter(location);
    }

    public void centerMap(GeoPoint location) {
        osmMap.getController().setCenter(location);
    }

    public void setMapAutoFollow(boolean enable) {
        if (enable) {
            mapAutoCenter = true;
            ImageView img = parent.findViewById(R.id.mapCenterOnGpsButton);
            img.setColorFilter(null);
            img.setTag("on");
            centerOnObserver();
            invalidate();
        } else {
            mapAutoCenter = false;
            ImageView img = parent.findViewById(R.id.mapCenterOnGpsButton);
            img.setColorFilter(Color.argb(150,200,200,200));
            img.setTag("");
        }
    }

    public void setMapTileSource(ITileSource tileSource) {
        osmMap.setTileSource(tileSource);
        TextView nameText = mapContainer.findViewById(R.id.mapSourceName);
        if (nameText != null) {
            nameText.setText(tileSource.name());
        }
        invalidate();
    }

    public void resetPOIs() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        final View loadStatus = mapContainer.findViewById(R.id.mapLoadingIndicator);
        if (loadStatus != null) {
            loadStatus.setVisibility(View.VISIBLE);
        }

        updateTask = new UiRelatedTask() {
            @Override
            protected Object doWork() {
                for (RadiusMarkerClusterer markerFolder: poiMarkersFolder.values()) {
                    if (isCanceled()) {
                        return null;
                    }
                    markerFolder.getItems().clear();
                }

                try {
                    for (MapMarkerElement poi : poiList.values()) {
                        if (isCanceled()) {
                            return null;
                        }

                        if (!poiMarkersFolder.containsKey(poi.getOverlayPriority())) {
                            poiMarkersFolder.put(poi.getOverlayPriority(), createClusterMarker(poi));
                        }

                        ArrayList<Marker> markerList = poiMarkersFolder.get(poi.getOverlayPriority()).getItems();
                        Marker poiMarker = buildMapMarker(poi);

                        if (!markerList.contains(poiMarker)) {
                            markerList.add(poiMarker);
                        }
                    }
                } catch (NullPointerException e) { //buildMapMarker may generate null pointer if view is terminated in the middle of execution.
                    return null;
                }

                for (Integer markerOrder: poiMarkersFolder.keySet()) {
                    if (isCanceled()) {
                        return null;
                    }

                    if (!osmMap.getOverlays().contains(poiMarkersFolder.get(markerOrder))) {
                        osmMap.getOverlays().add(poiMarkersFolder.get(markerOrder));
                    }

                    poiMarkersFolder.get(markerOrder).invalidate();
                }
                return null;
            }

            @Override
            protected void thenDoUiRelatedWork(Object o) {
                if (loadStatus != null) {
                    loadStatus.setVisibility(View.GONE);
                }
            }
        };

        //Use the dbExecutor to prevent concurrent exception.
        Constants.DB_EXECUTOR
                .execute(updateTask);
    }

    private RadiusMarkerClusterer createClusterMarker(MapMarkerElement poi) {
        RadiusMarkerClusterer result = new RadiusMarkerWithClickEvent(osmMap.getContext());
        result.setMaxClusteringZoomLevel((int)Constants.MAP_DEFAUL_ZOOM_LEVEL - 1);
        Bitmap icon = ((BitmapDrawable)poi.getOverlayIcon(parent)).getBitmap();
        result.setRadius(Math.max(icon.getHeight(), icon.getWidth()));
        result.setIcon(icon);

        return result;
    }

    private Marker buildMapMarker(final MapMarkerElement poi) {
        Drawable nodeIcon = poi.getIcon(parent);

        Marker nodeMarker = new GeoNodeMapMarker(osmMap, poi.getMarkerData());
        nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        nodeMarker.setPosition(poi.getGeoPoint());
        nodeMarker.setIcon(nodeIcon);

        if (showPoiInfoDialog) {
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

        Drawable nodeIcon = osmMap.getContext().getResources().getDrawable(R.drawable.ic_direction);

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
        deviceLocation = location;
        if (mapAutoCenter) {
            centerOnObserver();
        }
        obsLocationMarker.getPosition().setCoords(deviceLocation.getLatitude(), deviceLocation.getLongitude());
        obsLocationMarker.getPosition().setAltitude(deviceLocation.getAltitude());
    }
    public void onOrientationChange(double pAzimuth, double pPitch, double pRoll) {
        obsLocationMarker.setRotation(-(float) pAzimuth);
    }

    public void invalidate() {
        if (System.currentTimeMillis() - osmLastInvalidate < MAP_REFRESH_INTERVAL_MS) {
            return;
        }
        osmLastInvalidate = System.currentTimeMillis();

        osmMap.invalidate();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mapLayerToggleButton:
                flipLayerProvider(false);
                break;
            case R.id.mapCenterOnGpsButton:
                if (v.getTag() != "on") {
                    setMapAutoFollow(true);
                } else {
                    setMapAutoFollow(false);
                }
                break;
        }
    }
}
