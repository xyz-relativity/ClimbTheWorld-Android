package com.climbtheworld.app.widgets;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.MapQuestTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
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

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget implements View.OnClickListener {
    final ITileSource mapBoxTileSource;

    private final MapView osmMap;
    private final View mapContainer;
    private Marker.OnMarkerClickListener poiOnClickEvent;
    private boolean showPois = true;
    private Marker.OnMarkerClickListener obsOnClickEvent;
    private boolean showObserver = true;
    private FolderOverlay myLocationMarkersFolder = new FolderOverlay();
    private ScaleBarOverlay scaleBarOverlay;
    private RadiusMarkerClusterer poiMarkersFolder;
    private Marker obsLocationMarker;
    private long osmLasInvalidate;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    private Map<Long, GeoNode> poiList = new HashMap<>(); //database
    private boolean showPoiInfoDialog = true;
    private boolean mapAutoCenterOn = true;
    private FolderOverlay customMarkers;
    private AppCompatActivity parent;
    private Semaphore semaphore = new Semaphore(1);

    private static final int MAP_REFRESH_INTERVAL_MS = 1000;

    public MapViewWidget(AppCompatActivity pActivity,View pOsmMap, Map<Long, GeoNode> poiDB) {
        this(pActivity, pOsmMap, poiDB, null);
    }

    public MapViewWidget(AppCompatActivity pActivity, View pOsmMap, Map<Long, GeoNode> poiDB, FolderOverlay pCustomMarkers) {
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
                    setMapAutoCenterOn(false);
                }

                for (View.OnTouchListener listener: touchListeners) {
                    eventCaptured = eventCaptured || listener.onTouch(view, motionEvent);
                }

                return eventCaptured;
            }
        });

        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);
        osmMap.setUseDataConnection(Globals.allowMapDownload(parent.getApplicationContext()));

        osmMap.post(new Runnable() {
            @Override
            public void run() {
                setMapTileSource(TileSourceFactory.OpenTopo);
            }
        });

        resetPOIs();
        setShowObserver(this.showObserver, null);

        mapBoxTileSource = new MapQuestTileSource(parent);

        setMapButtonListener();
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

    public void centerOnObserver() {
        centerOnGoePoint(Globals.poiToGeoPoint(Globals.virtualCamera));
        invalidate ();
    }

    public void centerOnGoePoint(GeoPoint location) {
        osmMap.getController().setCenter(location);
        invalidate ();
    }

    public void setMapAutoCenterOn(boolean enable) {
        if (enable) {
            mapAutoCenterOn = true;
            ImageView img = parent.findViewById(R.id.mapCenterOnGpsButton);
            img.setColorFilter(null);
            img.setTag("on");
            invalidate();
        } else {
            mapAutoCenterOn = false;
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
        (new Thread() {
            public void run() {
                semaphore.acquireUninterruptibly();
                poiMarkersFolder = new RadiusMarkerClusterer(osmMap.getContext());
                poiMarkersFolder.setMaxClusteringZoomLevel((int)Constants.MAP_ZOOM_LEVEL - 1);

                osmMap.getOverlays().clear();
                if (customMarkers != null) {
                    osmMap.getOverlays().add(customMarkers);
                }

                osmMap.getOverlays().add(scaleBarOverlay);
                osmMap.getOverlays().add(myLocationMarkersFolder);
                osmMap.getOverlays().add(poiMarkersFolder);

                for (GeoNode poi : poiList.values()) {
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

    private void addMapMarker(final GeoNode poi) {
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
                    DialogBuilder.buildNodeInfoDialog(parent, poi).show();
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

        if (mapAutoCenterOn) {
            osmMap.getController().setCenter(Globals.poiToGeoPoint(Globals.virtualCamera));
        }

        obsLocationMarker.setRotation((float) Globals.virtualCamera.degAzimuth);
        obsLocationMarker.getPosition().setCoords(Globals.virtualCamera.decimalLatitude, Globals.virtualCamera.decimalLongitude);
        obsLocationMarker.getPosition().setAltitude(Globals.virtualCamera.elevationMeters);

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
                    setMapAutoCenterOn(true);
                } else {
                    setMapAutoCenterOn(false);
                }
                break;
        }
    }
}
