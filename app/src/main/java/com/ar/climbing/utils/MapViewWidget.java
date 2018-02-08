package com.ar.climbing.utils;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.ar.climbing.R;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget {
    private final MapView osmMap;
    private Marker.OnMarkerClickListener poiOnClickEvent;
    private boolean showPois = true;
    private Marker.OnMarkerClickListener obsOnClickEvent;
    private boolean showObserver = true;
    private FolderOverlay myLocationMarkersFolder = new FolderOverlay();
    private RadiusMarkerClusterer poiMarkersFolder;
    private Marker obsLocationMarker;
    private long osmMapClickTimer;
    private long osmLasInvalidate;
    private boolean doAutoCenter = true;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    private Map<Long, PointOfInterest> poiList = new HashMap<>(); //database
    private boolean showPoiInfoDialog = true;
    private boolean allowAutoCenter = true;
    private FolderOverlay customMarkers;
    private AppCompatActivity activity;
    private Semaphore semaphore = new Semaphore(1);

    private static final int MAP_REFRESH_INTERVAL_MS = 150;

    public MapViewWidget(AppCompatActivity pActivity, MapView pOsmMap, Map poiDB) {
        this(pActivity, pOsmMap, poiDB, null);
    }

    public MapViewWidget(AppCompatActivity pActivity, MapView pOsmMap, Map poiDB, FolderOverlay pCustomMarkers) {
        this.activity = pActivity;
        this.osmMap = pOsmMap;
        this.poiList = poiDB;
        this.customMarkers = pCustomMarkers;

        osmMapClickTimer = System.currentTimeMillis();

        osmMap.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                osmMapClickTimer = System.currentTimeMillis();
                doAutoCenter = false;

                for (View.OnTouchListener listener: touchListeners) {
                    listener.onTouch(view, motionEvent);
                }

                return false;
            }
        });

        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);
        osmMap.setMaxZoomLevel(Constants.MAP_MAX_ZOOM_LEVEL);
        osmMap.setUseDataConnection(Globals.allowDownload(activity.getApplicationContext()));

        resetPOIs();
        setShowObserver(this.showObserver, null);
    }

    public MapView getOsmMap() {
        return osmMap;
    }

    public void addTouchListener (View.OnTouchListener listener) {
        this.touchListeners.add(listener);
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

    public void centerOnObserver() {
        osmMap.getController().setCenter(obsLocationMarker.getPosition());
        invalidate ();
    }

    public void setAllowAutoCenter(boolean enable) {
        this.allowAutoCenter = enable;
    }

    public void setMapTileSource(ITileSource tileSource) {
        osmMap.setTileSource(tileSource);
        invalidate();
    }

    public void resetPOIs() {
        //this should probably be done in a thread
        (new Thread() {
            public void run() {
                semaphore.acquireUninterruptibly();
                poiMarkersFolder = new RadiusMarkerClusterer(osmMap.getContext());
                poiMarkersFolder.setMaxClusteringZoomLevel(Constants.MAP_ZOOM_LEVEL - 1);

                osmMap.getOverlays().clear();
                if (customMarkers != null) {
                    osmMap.getOverlays().add(customMarkers);
                }
                osmMap.getOverlays().add(myLocationMarkersFolder);
                osmMap.getOverlays().add(poiMarkersFolder);

                for (Long poiID : poiList.keySet()) {
                    PointOfInterest poi = poiList.get(poiID);
                    addMapMarker(poi);
                }

                poiMarkersFolder.invalidate();
                semaphore.release();
            }
        }).start();
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

    private void addMapMarker(final PointOfInterest poi) {
        ArrayList<Marker> list = poiMarkersFolder.getItems();

        Drawable nodeIcon = osmMap.getContext().getResources().getDrawable(R.drawable.ic_topo_small);
        nodeIcon.mutate(); //allow different effects for each marker.
        nodeIcon.setTintList(Globals.gradeToColorState(poi.getLevelId()));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

        Marker nodeMarker = new Marker(osmMap);
        nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        nodeMarker.setPosition(Globals.poiToGeoPoint(poi));
        nodeMarker.setIcon(nodeIcon);

        if (showPoiInfoDialog) {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    PointOfInterestDialogBuilder.buildDialog(activity, poi).show();
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
        list.add(nodeMarker);
    }

    public void invalidate () {
        if ((System.currentTimeMillis() - osmLasInvalidate < MAP_REFRESH_INTERVAL_MS) && (!showPois) && semaphore.availablePermits() < 1) {
            return;
        }
        semaphore.acquireUninterruptibly();
        osmLasInvalidate = System.currentTimeMillis();

        if (allowAutoCenter && (doAutoCenter || (System.currentTimeMillis() - osmMapClickTimer) > Constants.MAP_CENTER_FREES_TIMEOUT_MILLISECONDS)) {
            osmMap.getController().setCenter(Globals.poiToGeoPoint(Globals.observer));
            doAutoCenter = true;
        }

        obsLocationMarker.setRotation((float)Globals.observer.degAzimuth);
        obsLocationMarker.getPosition().setCoords(Globals.observer.decimalLatitude, Globals.observer.decimalLongitude);
        obsLocationMarker.getPosition().setAltitude(Globals.observer.elevationMeters);

        osmMap.invalidate();
        semaphore.release();
    }
}
