package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.osm.MarkerUtils;
import com.climbtheworld.app.osm.editor.ArtificialTags;
import com.climbtheworld.app.osm.editor.ContactTags;
import com.climbtheworld.app.osm.editor.CragTags;
import com.climbtheworld.app.osm.editor.GeneralTags;
import com.climbtheworld.app.osm.editor.ITags;
import com.climbtheworld.app.osm.editor.RouteTags;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.json.JSONException;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import needle.UiRelatedTask;

public class EditNodeActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, AdapterView.OnItemSelectedListener {
    private GeoNode editNode;
    Map<Long, MapViewWidget.MapMarkerElement> poiMap = new ConcurrentHashMap<>();
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private Spinner dropdownType;
    private ViewGroup containerTags;
    private GeneralTags genericTags;

    Map<GeoNode.NodeTypes, List<ITags>> nodeTypesTags = new HashMap<>();

    private Intent intent;
    private long editNodeID;

    private final static int locationUpdate = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_node);

        intent = getIntent();
        editNodeID = intent.getLongExtra("poiID", 0);

        doDatabaseWork(editNodeID);

        this.dropdownType = findViewById(R.id.spinnerNodeType);
        containerTags = findViewById(R.id.containerTags);

        mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), poiMap);
        mapWidget.setShowPoiInfoDialog(false);
        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getAction();
                ViewParent scrollParent = view.getParent();
                while (scrollParent != null && !(scrollParent instanceof ScrollView)) {
                    scrollParent = scrollParent.getParent();
                }

                if (scrollParent != null) {
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            // Disallow ScrollView to intercept touch events.
                            scrollParent.requestDisallowInterceptTouchEvent(true);
                            break;

                        case MotionEvent.ACTION_UP:
                            // Allow ScrollView to intercept touch events.
                            scrollParent.requestDisallowInterceptTouchEvent(false);
                            break;
                    }
                }

                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < Constants.ON_TAP_DELAY_MS)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());

                    editNode.updatePOILocation(gp.getLatitude(), gp.getLongitude(), editNode.elevationMeters);
                    updateMapMarker();
                    genericTags.updateLocation(); //update location text boxes.

                    return true;
                }
                return false;
            }
        });

        buildPopupMenu();

        //location
        locationHandler = new LocationHandler(EditNodeActivity.this, this, locationUpdate);
        locationHandler.addListener(this);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);
    }

    private void doDatabaseWork(final long poiId) {
        Constants.DB_EXECUTOR
                .execute(new UiRelatedTask<GeoNode>() {
                    @Override
                    protected GeoNode doWork() {
                        if (poiId == 0) {
                            GeoNode tmpPoi = new GeoNode(intent.getDoubleExtra("poiLat", Globals.virtualCamera.decimalLatitude),
                                    intent.getDoubleExtra("poiLon", Globals.virtualCamera.decimalLongitude),
                                    Globals.virtualCamera.elevationMeters);
                            tmpPoi.nodeType = GeoNode.NodeTypes.route;

                            long tmpID = Globals.appDB.nodeDao().getSmallestId();
                            if (tmpID >= 0) {
                                tmpID = -1L;
                            } else {
                                tmpID -= 1;
                            }
                            tmpPoi.osmID = tmpID;

                            return tmpPoi;
                        } else {
                            return Globals.appDB.nodeDao().loadNode(poiId);
                        }
                    }

                    @Override
                    protected void thenDoUiRelatedWork(GeoNode result) {
                        editNode = result;
                        buildUi();
                        updateMapMarker();
                    }
                });
    }

    private void buildPopupMenu() {
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Creating the instance of PopupMenu
                PopupMenu popup = new PopupMenu(EditNodeActivity.this, view);
                popup.getMenuInflater().inflate(R.menu.edit_options, popup.getMenu());

                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        String urlFormat;
                        Intent intent;

                        switch (item.getItemId()) {
                            case R.id.advanceEditor:
                                intent = new Intent(EditNodeActivity.this, EditNodeAdvancedActivity.class);
                                intent.putExtra("nodeJson", editNode.toJSONString());
                                startActivityForResult(intent, 0);
                                break;

                            case R.id.openStreetMapEditor:
                                if (editNodeID > 0) {
                                    urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/edit?node=%d",
                                            editNode.getID());
                                } else {
                                    urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/edit#map=21/%f/%f",
                                            editNode.decimalLatitude, editNode.decimalLongitude);
                                }

                                intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(urlFormat));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                EditNodeActivity.this.startActivity(intent);
                                finish();
                                break;

                            case R.id.vespucci:
                                BoundingBox bbox = DataManager.computeBoundingBox(new Quaternion(editNode.decimalLatitude, editNode.decimalLongitude, editNode.elevationMeters, 0), 10);
                                urlFormat = String.format(Locale.getDefault(), "josm:/load_and_zoom?left=%f&bottom=%f&right=%f&top=%f",
                                        bbox.getLonWest(), bbox.getLatSouth(), bbox.getLonEast(), bbox.getLatNorth());

                                if (editNodeID > 0) {
                                    urlFormat = urlFormat + "&select=" + editNodeID;
                                }

                                try {
                                    intent = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(urlFormat));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    EditNodeActivity.this.startActivity(intent);
                                    finish();
                                } catch (ActivityNotFoundException e) {
                                    DialogBuilder.showErrorDialog(EditNodeActivity.this, getResources().getString(R.string.no_josm_app), null);
                                }
                                break;
                        }
                        return true;
                    }
                });
                popup.show();
            }
        });
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        switch (parent.getId()) {
            case R.id.spinnerNodeType:
                switchNodeType(GeoNode.NodeTypes.values()[pos]);
                updateMapMarker();
                break;
        }
    }

    private void switchNodeType (GeoNode.NodeTypes type) {
        for (ITags tags: nodeTypesTags.get(editNode.nodeType)) {
            tags.hideTags();
        }

        editNode.nodeType = type;

        for (ITags tags: nodeTypesTags.get(editNode.nodeType)) {
            tags.showTags();
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void buildUi() {
        poiMap.clear();
        poiMap.put(editNode.getID(), new MarkerGeoNode(editNode));
        mapWidget.centerMap(Globals.poiToGeoPoint(editNode));

        GeneralTags generalTags = new GeneralTags(editNode, this, containerTags, this);
        ITags routeTags = new RouteTags(editNode, this, containerTags);
        ITags cragTags = new CragTags(editNode, this, containerTags);
        ITags artificialTags = new ArtificialTags(editNode, this, containerTags);
        ITags contactInfoTags = new ContactTags(editNode, this, containerTags);
        this.genericTags = generalTags;

        List<ITags> tags = new ArrayList<>();
        tags.add(generalTags);
        tags.add(routeTags);
        tags.add(contactInfoTags);
        nodeTypesTags.put(GeoNode.NodeTypes.route, tags);

        tags = new ArrayList<>();
        tags.add(generalTags);
        tags.add(cragTags);
        tags.add(contactInfoTags);
        nodeTypesTags.put(GeoNode.NodeTypes.crag, tags);

        tags = new ArrayList<>();
        tags.add(generalTags);
        tags.add(artificialTags);
        tags.add(contactInfoTags);
        nodeTypesTags.put(GeoNode.NodeTypes.artificial, tags);

        tags = new ArrayList<>();
        tags.add(generalTags);
        nodeTypesTags.put(GeoNode.NodeTypes.unknown, tags);

        dropdownType.setOnItemSelectedListener(this);
        dropdownType.setAdapter(new MarkerUtils.SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GeoNode.NodeTypes.values(), editNode));
        dropdownType.setSelection(Arrays.asList(GeoNode.NodeTypes.values()).indexOf(editNode.nodeType));
        if (editNodeID == 0) {
            dropdownType.performClick();
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ButtonCancel:
                finish();
                break;

            case R.id.ButtonSave:
                for (ITags tags: nodeTypesTags.get(editNode.nodeType)) {
                    tags.SaveToNode(editNode);
                }

                editNode.updateDate = System.currentTimeMillis();
                editNode.localUpdateState = GeoNode.TO_UPDATE_STATE;
                Constants.DB_EXECUTOR
                        .execute(new UiRelatedTask<Boolean>() {
                            @Override
                            protected Boolean doWork() {
                                if (editNode.osmID < 0 && editNode.localUpdateState == GeoNode.TO_DELETE_STATE) {
                                    Globals.appDB.nodeDao().deleteNodes(editNode);
                                } else {
                                    Globals.appDB.nodeDao().insertNodesWithReplace(editNode);
                                }

                                return true;
                            }

                            @Override
                            protected void thenDoUiRelatedWork(Boolean result) {
                                finish();
                            }
                        });

                finish();
                break;

            case R.id.ButtonDelete:
                new AlertDialog.Builder(this)
                        .setTitle(getResources().getString(R.string.delete_confirmation , editNode.getName()))
                        .setMessage(R.string.delete_confirmation_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                editNode.updateDate = System.currentTimeMillis();
                                editNode.localUpdateState = GeoNode.TO_DELETE_STATE;

                                Constants.DB_EXECUTOR
                                        .execute(new UiRelatedTask<Boolean>() {
                                            @Override
                                            protected Boolean doWork() {
                                                if (editNode.osmID < 0 && editNode.localUpdateState == GeoNode.TO_DELETE_STATE) {
                                                    Globals.appDB.nodeDao().deleteNodes(editNode);
                                                } else {
                                                    Globals.appDB.nodeDao().insertNodesWithReplace(editNode);
                                                }

                                                return true;
                                            }

                                            @Override
                                            protected void thenDoUiRelatedWork(Boolean result) {
                                                finish();
                                            }
                                        });
                            }})
                        .setNegativeButton(android.R.string.no, null).show();
                break;
        }
    }

    public void updateMapMarker() {
        mapWidget.resetPOIs();
        mapWidget.invalidate();
    }

    @Override
    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.virtualCamera.degAzimuth = pAzimuth;
        Globals.virtualCamera.degPitch = pPitch;
        Globals.virtualCamera.degRoll = pRoll;

        mapWidget.onOrientationChange(pAzimuth, pPitch, pRoll);
        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.onLocationChange();
        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);
        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        Globals.onPause(this);
        sensorManager.unregisterListener(sensorListener);

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(v).show();
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            String nodeJson = data.getStringExtra("nodeJson");
            try {
                editNode = new GeoNode(nodeJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            buildUi();
        }
    }
}
