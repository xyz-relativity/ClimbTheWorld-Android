package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationManager;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.NodeDisplayFilters;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;
import com.climbtheworld.app.utils.marker.MarkerUtils;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import androidx.appcompat.app.AppCompatActivity;
import needle.UiRelatedTask;

import static com.climbtheworld.app.widgets.MapViewWidget.MAP_CENTER_ON_ZOOM_LEVEL;

public class ViewMapActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, DisplayFilterFragment.OnFilterChangeListener {
    private MapViewWidget mapWidget;
    private OrientationManager orientationManager;
    private LocationManager locationManager;
    private View loading;

    private FolderOverlay tapMarkersFolder = new FolderOverlay();
    private Marker tapMarker;
    private DataManager downloadManager;
    private ConcurrentHashMap<Long, DisplayableGeoNode> allPOIs = new ConcurrentHashMap<>();

    private UiRelatedTask dbTask = null;

    private static final int LOCATION_UPDATE = 500;
    private Configs configs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        configs = Configs.instance(this);

        Ask.on(this)
                .id(500) // in case you are invoking multiple time Ask from same activity or fragment
                .forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withRationales(getString(R.string.map_location_rational)) //optional
                .go();

        loading = findViewById(R.id.mapLoadingIndicator);

        mapWidget = MapWidgetFactory.buildMapView(this, tapMarkersFolder, false);
        initTapMarker();

        setEventListeners();

        Intent intent = getIntent();
        if (intent!= null && intent.hasExtra("GeoPoint")) {
            GeoPoint location = GeoPoint.fromDoubleString(intent.getStringExtra("GeoPoint"), ',');
            centerOnLocation(location);
            mapWidget.setMapAutoFollow(false);
        }

        this.downloadManager = new DataManager(this);

        //location
        locationManager = new LocationManager(this, LOCATION_UPDATE);
        locationManager.addListener(this);

        orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
        orientationManager.addListener(this);

        FloatingActionButton createNew = findViewById(R.id.createButton);
        createNew.setImageDrawable(MarkerUtils.getLayoutIcon(this, R.layout.icon_node_add_display, 255));
        createNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ViewMapActivity.this, EditNodeActivity.class);
                intent.putExtra("poiLat", tapMarker.getPosition().getLatitude());
                intent.putExtra("poiLon", tapMarker.getPosition().getLongitude());
                startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
            }
        });
    }

    private void setEventListeners() {
        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < Constants.ON_TAP_DELAY_MS)) {
                    Point screenCoord = new Point();
                    mapWidget.getOsmMap().getProjection().unrotateAndScalePoint((int)motionEvent.getX(), (int)motionEvent.getY(), screenCoord);
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) screenCoord.x, (int) screenCoord.y);
                    tapMarker.setPosition(gp);
                }
                return false;
            }
        });

        mapWidget.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updatePOIs(false);
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updatePOIs(false);
                return false;
            }
        }));
    }

    private void updatePOIs(final boolean cleanState) {
        if (cleanState) {
            if (NodeDisplayFilters.hasFilters(configs)) {
                ((FloatingActionButton)findViewById(R.id.filterButton)).setImageResource(R.drawable.ic_filter_active);
            } else {
                ((FloatingActionButton)findViewById(R.id.filterButton)).setImageResource(R.drawable.ic_filter);
            }
        }

        final BoundingBox bBox = mapWidget.getOsmMap().getBoundingBox();

        loading.setVisibility(View.VISIBLE);

        if (dbTask != null) {
            dbTask.cancel();
        }

        dbTask = new UiRelatedTask() {
            @Override
            protected Object doWork() {
                if (!isCanceled()) {
                    allPOIs.clear();
                }

                boolean result = downloadManager.loadBBox(bBox, allPOIs);
                return (result || allPOIs.isEmpty()) && !isCanceled();
            }

            @Override
            protected void thenDoUiRelatedWork(Object o) {
                loading.setVisibility(View.GONE);
                if ((boolean)o) {
                    mapWidget.setClearState(cleanState);
                    mapWidget.refreshPOIs(new ArrayList<DisplayableGeoNode>(allPOIs.values()));
                }
            }
        };

        Constants.DB_EXECUTOR
                .execute(dbTask);
    }

    @Override
    public void updateOrientation(OrientationManager.OrientationEvent event) {
        mapWidget.onOrientationChange(event);
        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.onLocationChange(Globals.poiToGeoPoint(Globals.virtualCamera));
        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);
        mapWidget.onResume();

        locationManager.onResume();
        orientationManager.onResume();

        findViewById(R.id.mapViewContainer).post(new Runnable() {
            @Override
            public void run() {
                updatePOIs(true);
            }
        });
    }

    @Override
    protected void onPause() {
        locationManager.onPause();
        orientationManager.onPause();

        Globals.onPause(this);
        mapWidget.onPause();

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
            updatePOIs(true);
        }
    }

    private void initTapMarker() {
        List<Overlay> list = tapMarkersFolder.getItems();

        list.clear();

        Drawable nodeIcon = getResources().getDrawable(R.drawable.ic_tap_marker);

        tapMarker = new Marker(mapWidget.getOsmMap());
        tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        tapMarker.setIcon(nodeIcon);
        tapMarker.setImage(nodeIcon);
        tapMarker.setInfoWindow(null);
        tapMarker.setPosition(Globals.poiToGeoPoint(Globals.virtualCamera));

        //put into FolderOverlay list
        list.add(tapMarker);
    }

    public void centerOnLocation (GeoPoint location) {
        centerOnLocation(location, MAP_CENTER_ON_ZOOM_LEVEL);
    }

    public void centerOnLocation (GeoPoint location, Double zoom) {
        tapMarker.setPosition(location);
        mapWidget.setMapAutoFollow(false);
        mapWidget.centerOnGoePoint(location, zoom);
    }

    @Override
    public void onFilterChange() {
        updatePOIs(true);
    }

    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.filterButton:
                NodeDialogBuilder.showFilterDialog(this, this);
                break;

            case R.id.downloadButton:
                intent = new Intent(ViewMapActivity.this, NodesDataManagerActivity.class);
                startActivity(intent);
                break;
        }
    }
}

