package com.ar.openClimbAR.utils;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterestDialogBuilder;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private FolderOverlay poiMarkersFolder = new FolderOverlay();
    private Marker obsLocationMarker;
    private long osmMapClickTimer;
    private boolean doAutoCenter = true;
    private List<View.OnTouchListener> touchListeners = new ArrayList<>();

    public Map<Long, PointOfInterest> poiList = new ConcurrentHashMap<>(); //database
    private boolean showPoiInfoDialog = true;
    private boolean allowAutoCenter = true;

    private BoundingBox mapBox;

    public MapViewWidget(MapView pOsmMap, Map poiDB) {
        this.osmMap = pOsmMap;
        this.poiList = poiDB;
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

        osmMap.getOverlays().clear();
        osmMap.getOverlays().add(myLocationMarkersFolder);
        osmMap.getOverlays().add(poiMarkersFolder);

        setShowObserver(this.showObserver, null);

        mapBox = osmMap.getBoundingBox();
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

    private void initMyLocationMarkers() {
        List<Overlay> list = myLocationMarkersFolder.getItems();

        list.clear();

        Drawable nodeIcon = osmMap.getContext().getResources().getDrawable(R.drawable.direction_arrow);
        nodeIcon.mutate(); //allow different effects for each marker.

        obsLocationMarker = new Marker(osmMap);
        obsLocationMarker.setAnchor(0.5f, 0.5f);
        obsLocationMarker.setIcon(nodeIcon);
        obsLocationMarker.setImage(nodeIcon);
        obsLocationMarker.setInfoWindow(null);

        obsLocationMarker.setOnMarkerClickListener(obsOnClickEvent);

        //put into FolderOverlay list
        list.add(obsLocationMarker);

        myLocationMarkersFolder.closeAllInfoWindows();
    }

    private void addMapMarker(final PointOfInterest poi) {
        List<Overlay> list = poiMarkersFolder.getItems();

        Drawable nodeIcon = osmMap.getContext().getResources().getDrawable(R.drawable.marker_default);
        nodeIcon.mutate(); //allow different effects for each marker.

        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevelId());
        nodeIcon.setTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

        Marker nodeMarker = new Marker(osmMap);
        nodeMarker.setAnchor(0.5f, 1f);
        nodeMarker.setPosition(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
        nodeMarker.setIcon(nodeIcon);
        nodeMarker.setTitle(GradeConverter.getConverter().getGradeFromOrder("UIAA", poi.getLevelId()) +" (UIAA)");
        nodeMarker.setSubDescription(poi.name);
        nodeMarker.setImage(nodeIcon);

        if (showPoiInfoDialog) {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    PointOfInterestDialogBuilder.buildDialog(mapView.getContext(), poi, GlobalVariables.observer).show();
                    return true;
                }
            });
        } else {
            nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    return true;
                }
            });
        }

        //put into FolderOverlay list
        list.add(nodeMarker);

        poiMarkersFolder.closeAllInfoWindows();
    }

    public void invalidate () {
        if (allowAutoCenter && (doAutoCenter || (System.currentTimeMillis() - osmMapClickTimer) > Constants.MAP_CENTER_FREES_TIMEOUT_MILLISECONDS)) {
            osmMap.getController().setCenter(new GeoPoint(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude));
            doAutoCenter = true;
        }

        if (showPois) {
            if (!mapBox.equals(osmMap.getBoundingBox())) {
                poiMarkersFolder.getItems().clear();
                mapBox = osmMap.getBoundingBox();

                for (Long poiID : poiList.keySet()) {
                    PointOfInterest poi = poiList.get(poiID);
                    if ((poi.decimalLatitude > mapBox.getLatSouth() && poi.decimalLatitude < mapBox.getLatNorth())
                            && (poi.decimalLongitude > mapBox.getLonWest() && poi.decimalLongitude < mapBox.getLonEast())) {

                        addMapMarker(poi);
                    }
                }
            }
        } else {
            poiMarkersFolder.getItems().clear();
        }

        obsLocationMarker.setRotation(GlobalVariables.observer.degAzimuth);
        obsLocationMarker.getPosition().setCoords(GlobalVariables.observer.decimalLatitude, GlobalVariables.observer.decimalLongitude);
        obsLocationMarker.getPosition().setAltitude(GlobalVariables.observer.elevationMeters);

        osmMap.invalidate();
    }
}
