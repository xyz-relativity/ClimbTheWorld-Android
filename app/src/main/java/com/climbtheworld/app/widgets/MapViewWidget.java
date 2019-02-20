package com.climbtheworld.app.widgets;

import android.app.AlertDialog;
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
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
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
import java.util.concurrent.Semaphore;

/**
 * Created by xyz on 1/19/18.
 */

public class MapViewWidget implements View.OnClickListener {

    public interface MapMarkerElement {
        GeoPoint getGeoPoint();
        Drawable getIcon(AppCompatActivity parent);
        int getOverlayPriority();
        Drawable getOverlayIcon(AppCompatActivity parent);
        AlertDialog getOnClickDialog(AppCompatActivity parent);
        GeoNode getGeoNode();
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
    private Semaphore semaphore = new Semaphore(1);

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
                    NodeDialogBuilder.buildClusterDialog(parent, cluster).show();
                    return false;
                }
            });
            return m;
        }
    }

    public class GeoNodeMapMarker extends Marker {
        private GeoNode poi;
        public GeoNodeMapMarker(MapView mapView, GeoNode poi) {
            super(mapView);
            this.poi = poi;
        }

        public GeoNode getGeoNode() {
            return poi;
        }
    }

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
        osmMap.setUseDataConnection(Globals.allowMapDownload(parent.getApplicationContext()));
        osmMap.setScrollableAreaLimitLatitude(tileSystem.getMaxLatitude() - 0.1,-tileSystem.getMaxLatitude() + 0.1, 0);

        osmMap.getController().setCenter(Globals.poiToGeoPoint(Globals.virtualCamera));

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

    public void centerOnGoePoint(GeoPoint location) {
        centerOnGoePoint(location, osmMap.getZoomLevelDouble());
    }

    public void centerOnGoePoint(GeoPoint location, Double zoom) {
        osmMap.getController().animateTo(location, zoom, 1000L);
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
        //this should probably be done in a thread
        Constants.DB_EXECUTOR
                .execute(new Runnable() {
            @Override
            public void run() {
                semaphore.acquireUninterruptibly();

                for (RadiusMarkerClusterer markerFolder: poiMarkersFolder.values()) {
                    markerFolder.getItems().clear();
                }

                for (MapMarkerElement poi : poiList.values()) {
                    if (!poiMarkersFolder.containsKey(poi.getOverlayPriority())) {
                        poiMarkersFolder.put(poi.getOverlayPriority(), createClusterMarker(poi));
                    }

                    ArrayList<Marker> markerList = poiMarkersFolder.get(poi.getOverlayPriority()).getItems();
                    Marker poiMarker = buildMapMarker(poi);
                    if (!markerList.contains(poiMarker)) {
                        markerList.add(poiMarker);
                    }
                }

                for (Integer markerOrder: poiMarkersFolder.keySet()) {
                    if (!osmMap.getOverlays().contains(poiMarkersFolder.get(markerOrder))) {
                        osmMap.getOverlays().add(poiMarkersFolder.get(markerOrder));
                    }

                    poiMarkersFolder.get(markerOrder).invalidate();
                }

                semaphore.release();
            }
        });
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

        Marker nodeMarker = new GeoNodeMapMarker(osmMap, poi.getGeoNode());
        nodeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        nodeMarker.setPosition(poi.getGeoPoint());
        nodeMarker.setIcon(nodeIcon);
//        nodeMarker.setId(poi.getGeoNode().toJSONString());

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
        if ((System.currentTimeMillis() - osmLastInvalidate < MAP_REFRESH_INTERVAL_MS) || semaphore.availablePermits() < 1) {
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
                    setMapAutoFollow(true);
                } else {
                    setMapAutoFollow(false);
                }
                break;
        }
    }
}
