package com.climbtheworld.app.widgets;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.MappingUtils;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.events.MapListener;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.MapQuestTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import needle.Needle;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget implements View.OnClickListener {

    public interface MapMarkerElement {
        GeoPoint getGeoPoint();
        Drawable getIcon(AppCompatActivity parent);
        int getOverlayPriority();
        int getOverlayColor(int priority);
        Drawable getOverlayIcon(AppCompatActivity parent);
        AlertDialog getOnClickDialog(AppCompatActivity parent);
    }

    final ITileSource mapBoxTileSource;

    private final MapView osmMap;
    private final View mapContainer;
    private Marker.OnMarkerClickListener poiOnClickEvent;
    private boolean showPois = true;
    private Marker.OnMarkerClickListener obsOnClickEvent;
    private boolean showObserver = true;
    private FolderOverlay myLocationMarkersFolder = new FolderOverlay();
    private ScaleBarOverlay scaleBarOverlay;
    private Map<Integer, RadiusMarkerClusterer> poiMarkersFolder = new HashMap<>();
    private Marker obsLocationMarker;
    private long osmLastInvalidate;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    private Map<Long, ? extends MapMarkerElement> poiList; //database
    private boolean showPoiInfoDialog = true;
    private boolean mapAutoCenter = true;
    private FolderOverlay customMarkers;
    private AppCompatActivity parent;
    private Semaphore semaphore = new Semaphore(1);

    private static final int MAP_REFRESH_INTERVAL_MS = 1000;

    public MapViewWidget(AppCompatActivity pActivity,View pOsmMap, Map<Long, ? extends MapMarkerElement> poiDB) {
        this(pActivity, pOsmMap, poiDB, null);
    }

    public MapViewWidget(AppCompatActivity pActivity, View pOsmMap, Map<Long, ? extends MapMarkerElement> poiDB, FolderOverlay pCustomMarkers) {
        this.parent = pActivity;
        this.mapContainer = pOsmMap;
        this.osmMap = mapContainer.findViewById(R.id.openMapView);
        this.poiList = poiDB;
        this.customMarkers = pCustomMarkers;

        scaleBarOverlay = new ScaleBarOverlay(osmMap);
        scaleBarOverlay.setAlignBottom(true);
        scaleBarOverlay.setAlignRight(true);
        scaleBarOverlay.setEnableAdjustLength(true);

        osmMap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                boolean eventCaptured = false;
                if ((motionEvent.getAction() == MotionEvent.ACTION_MOVE)) {
                    setMapAutoCenter(false);
                }

                for (View.OnTouchListener listener: touchListeners) {
                    eventCaptured = eventCaptured || listener.onTouch(view, motionEvent);
                }

                return eventCaptured;
            }
        });

//        final TileSystem tileSystem = new TileSystemWebMercator();

        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);
        osmMap.setUseDataConnection(Globals.allowMapDownload(parent.getApplicationContext()));
        osmMap.setScrollableAreaLimitLatitude(TileSystem.MaxLatitude,-TileSystem.MaxLatitude, 0);

        osmMap.getController().setCenter(Globals.poiToGeoPoint(Globals.virtualCamera));

        osmMap.post(new Runnable() {
            @Override
            public void run() {
                setMapTileSource(TileSourceFactory.OpenTopo);
                osmMap.setMinZoomLevel(TileSystem.getLatitudeZoom(TileSystem.MaxLatitude,-TileSystem.MaxLatitude, mapContainer.getHeight()));
            }
        });

        resetPOIs();
        setShowObserver(this.showObserver, null);

        mapBoxTileSource = new MapQuestTileSource(parent);

        setMapButtonListener();
        setMapAutoCenter(true);
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

        double providerMaxZoom = (double) getOsmMap().getTileProvider().getTileSource().getMaximumZoomLevel();
        if ((getOsmMap().getZoomLevelDouble() > providerMaxZoom) && (resetZoom)) {
            getOsmMap().getController().setZoom(providerMaxZoom);
        }

        setMapTileSource(tilesProvider);
    }

    public void setShowPOIs (boolean enable) {
        this.showPois = enable;
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

    public void setShowPoiInfoDialog (boolean enable) {
        this.showPoiInfoDialog = enable;
    }

    private void centerOnObserver() {
        centerOnGoePoint(Globals.poiToGeoPoint(Globals.virtualCamera));
    }

    private void centerOnGoePoint(GeoPoint location) {
        osmMap.getController().animateTo(location);
    }

    public void setMapAutoCenter(boolean enable) {
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
        //this should probably be done in a thread
        Constants.DB_EXECUTOR
                .execute(new Runnable() {
            @Override
            public void run() {
                semaphore.acquireUninterruptibly();
                osmMap.getOverlays().clear();
                if (customMarkers != null) {
                    osmMap.getOverlays().add(customMarkers);
                }

                osmMap.getOverlays().add(scaleBarOverlay);
                osmMap.getOverlays().add(myLocationMarkersFolder);

                for (Integer markerOrder: poiMarkersFolder.keySet()) {
                    poiMarkersFolder.get(markerOrder).getItems().clear();
                }

                for (MapMarkerElement poi : poiList.values()) {
                    if (!poiMarkersFolder.containsKey(poi.getOverlayPriority())) {
                        poiMarkersFolder.put(poi.getOverlayPriority(), createClusterMarker(poi.getOverlayColor(poi.getOverlayPriority())));
                    }

                    ArrayList<Marker> markerList = poiMarkersFolder.get(poi.getOverlayPriority()).getItems();
                    markerList.add(addMapMarker(poi));
                }

                for (Integer markerOrder: poiMarkersFolder.keySet()) {
                    osmMap.getOverlays().add(poiMarkersFolder.get(markerOrder));
                    poiMarkersFolder.get(markerOrder).invalidate();
                }

                semaphore.release();
            }
        });
    }

    private RadiusMarkerClusterer createClusterMarker(int color) {
        int originalW = 300;
        int originalH = 300;
        RadiusMarkerClusterer result = new RadiusMarkerClusterer(osmMap.getContext());
        result.setMaxClusteringZoomLevel((int)Constants.MAP_ZOOM_LEVEL - 1);
        Drawable nodeIcon = parent.getResources().getDrawable(R.drawable.ic_clusters);
        nodeIcon.mutate(); //allow different effects for each marker.
        nodeIcon.setTintList(ColorStateList.valueOf(color));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
        result.setIcon(MappingUtils.getBitmap((VectorDrawable)nodeIcon, originalW, originalH, Constants.POI_ICON_SIZE_MULTIPLIER));

        return result;
    }

    private Marker addMapMarker(final MapMarkerElement poi) {
        Drawable nodeIcon = poi.getIcon(parent);

        Marker nodeMarker = new Marker(osmMap);
        nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        nodeMarker.setPosition(poi.getGeoPoint());
        nodeMarker.setIcon(nodeIcon);

        if (showPoiInfoDialog) {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    poi.getOnClickDialog(parent).show();
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

        Drawable nodeIcon = osmMap.getContext().getResources().getDrawable(R.drawable.direction_arrow);
        nodeIcon.mutate(); //allow different effects for each marker.

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

    public void onLocationChange() {
        if (mapAutoCenter) {
            centerOnObserver();
        }
        obsLocationMarker.getPosition().setCoords(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude);
        obsLocationMarker.getPosition().setAltitude(Globals.virtualCamera.elevationMeters);
    }
    public void onOrientationChange(double pAzimuth, double pPitch, double pRoll) {
        obsLocationMarker.setRotation((float) pAzimuth);
    }

    public void invalidate() {
        if ((System.currentTimeMillis() - osmLastInvalidate < MAP_REFRESH_INTERVAL_MS) && (!showPois) && semaphore.availablePermits() < 1) {
            return;
        }
        semaphore.acquireUninterruptibly();
        osmLastInvalidate = System.currentTimeMillis();

        osmMap.invalidate();
        semaphore.release();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mapLayerToggleButton:
                flipLayerProvider(false);
                break;
            case R.id.mapCenterOnGpsButton:
                if (v.getTag() != "on") {
                    setMapAutoCenter(true);
                } else {
                    setMapAutoCenter(false);
                }
                break;
        }
    }
}
