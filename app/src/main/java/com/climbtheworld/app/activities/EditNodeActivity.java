package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
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
import com.climbtheworld.app.openstreetmap.editor.ArtificialTags;
import com.climbtheworld.app.openstreetmap.editor.ContactTags;
import com.climbtheworld.app.openstreetmap.editor.CragTags;
import com.climbtheworld.app.openstreetmap.editor.GeneralTags;
import com.climbtheworld.app.openstreetmap.editor.ITags;
import com.climbtheworld.app.openstreetmap.editor.OtherTags;
import com.climbtheworld.app.openstreetmap.editor.RouteTags;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationManager;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;
import com.climbtheworld.app.utils.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.marker.MarkerUtils;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.appcompat.app.AppCompatActivity;
import needle.UiRelatedTask;

public class EditNodeActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
    private GeoNode editNode;
    ConcurrentHashMap<Long, DisplayableGeoNode> poiMap = new ConcurrentHashMap<>();
    private MapViewWidget mapWidget;
    private LocationManager locationManager;
    private OrientationManager orientationManager;
    private Spinner dropdownType;
    private ViewGroup containerTags;
    private GeneralTags genericTags;
    private DataManager downloadManager;

    private Map<GeoNode.NodeTypes, List<ITags>> nodeTypesTags = new HashMap<>();
    private List<ITags> allTagsHandlers = new ArrayList<>();

    private Intent intent;
    private long editNodeID;

    private final static int locationUpdate = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_node);

        intent = getIntent();
        editNodeID = intent.getLongExtra("poiID", 0);

        this.downloadManager = new DataManager(this);

        doDatabaseWork(editNodeID);

        this.dropdownType = findViewById(R.id.spinnerNodeType);
        containerTags = findViewById(R.id.containerTags);

        mapWidget = MapWidgetFactory.buildMapView(this);
        mapWidget.resetZoom();
        mapWidget.setMapAutoFollow(false);
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
                    Point screenCoord = new Point();
                    mapWidget.getOsmMap().getProjection().unrotateAndScalePoint((int)motionEvent.getX(), (int)motionEvent.getY(), screenCoord);
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) screenCoord.x, (int) screenCoord.y);

                    editNode.updatePOILocation(gp.getLatitude(), gp.getLongitude(), editNode.elevationMeters);
                    updateMapMarker();
                    genericTags.updateLocation(); //update location text boxes.

                    return true;
                }
                return false;
            }
        });

        mapWidget.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                downloadBBox();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                downloadBBox();
                return false;
            }
        }));

        buildPopupMenu();

        //location
        locationManager = new LocationManager(this, locationUpdate);
        locationManager.addListener(this);

        orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
        orientationManager.addListener(this);
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

                            tmpPoi.setNodeType(GeoNode.NodeTypes.route);
                            tmpPoi.osmID = Globals.getNewNodeID();

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

    private void downloadBBox() {
        Constants.DB_EXECUTOR
                .execute(new UiRelatedTask<Boolean>() {
                    @Override
                    protected Boolean doWork() {
                        boolean result = false;
                        if(Math.floor(mapWidget.getOsmMap().getZoomLevelDouble()) > MapViewWidget.CLUSTER_ZOOM_LEVEL) {
                            ConcurrentHashMap<Long, DisplayableGeoNode> hiddenPois = new ConcurrentHashMap<>();
                            result = downloadManager.loadBBox(mapWidget.getOsmMap().getBoundingBox(), hiddenPois);

                            for (DisplayableGeoNode point : hiddenPois.values()) {
                                if (!poiMap.containsKey(point.getGeoNode().getID())) {
                                    point.setGhost(true);
                                    poiMap.put(point.getGeoNode().getID(), point);
                                }
                            }
                        } else {
                            for (DisplayableGeoNode point : poiMap.values()) {
                                if (point.getGeoNode().getID() != editNode.getID()) {
                                    poiMap.remove(point);
                                    result = true;
                                }
                            }
                        }

                        return result;
                    }

                    @Override
                    protected void thenDoUiRelatedWork(Boolean result) {
                        if (result) {
                            updateMapMarker();
                        }
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

                                GeoNode tempNode = new GeoNode(editNode.jsonNodeInfo);
                                synchronizeNode(tempNode);

                                intent.putExtra("nodeJson", tempNode.toJSONString());
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

    private void buildNodeFragments() {
        GeneralTags generalTags = new GeneralTags(editNode, this, containerTags, this);
        ITags routeTags = new RouteTags(editNode, this, containerTags);
        ITags cragTags = new CragTags(editNode, this, containerTags);
        ITags artificialTags = new ArtificialTags(editNode, this, containerTags);
        ITags contactInfoTags = new ContactTags(editNode, this, containerTags);
        ITags otherTags = new OtherTags(editNode, this, containerTags);

        this.genericTags = generalTags;
        allTagsHandlers.add(generalTags);
        allTagsHandlers.add(routeTags);
        allTagsHandlers.add(cragTags);
        allTagsHandlers.add(artificialTags);
        allTagsHandlers.add(contactInfoTags);
        allTagsHandlers.add(otherTags);

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
        tags.add(otherTags);
        nodeTypesTags.put(GeoNode.NodeTypes.unknown, tags);
    }

    private void buildUi() {
        poiMap.clear();
        poiMap.put(editNode.getID(), new DisplayableGeoNode(editNode, false));
        mapWidget.centerOnGoePoint(Globals.poiToGeoPoint(editNode));

        buildNodeFragments();

        dropdownType.setOnItemSelectedListener(null);
        dropdownType.setAdapter(new MarkerUtils.SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GeoNode.NodeTypes.values(), editNode));
        int pos = Arrays.asList(GeoNode.NodeTypes.values()).indexOf(editNode.getNodeType());
        dropdownType.setSelection(pos);
        dropdownType.setTag(pos);
        if (editNodeID == 0) {
            dropdownType.performClick();
        }
        dropdownType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
//                if((int)dropdownType.getTag() != pos) //this is used to prevent self on select event.
                {
                    dropdownType.setTag(pos);
                    switchNodeType(GeoNode.NodeTypes.values()[pos]);
                    updateMapMarker();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void switchNodeType (GeoNode.NodeTypes type) {
        for (ITags tags: nodeTypesTags.get(editNode.getNodeType())) {
            tags.hideTags();
        }

        editNode.setNodeType(type);

        for (ITags tags: nodeTypesTags.get(editNode.getNodeType())) {
            tags.showTags();
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ButtonCancel:
                finish();
                break;

            case R.id.ButtonSave:
                if (synchronizeNode(editNode)) {

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
                }
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
        mapWidget.resetPOIs(new ArrayList<>(poiMap.values()), false);
    }

    private boolean synchronizeNode(GeoNode node) {
        boolean success = true;
        List<ITags> activeTags = nodeTypesTags.get(node.getNodeType());
        for (ITags tag: allTagsHandlers) {
            if (!activeTags.contains(tag)) {
                tag.cancelNode(node);
            }
        }

        for (ITags tags: activeTags) {
            success = tags.saveToNode(node);
        }
        return success;
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
    }

    @Override
    protected void onPause() {
        locationManager.onPause();
        orientationManager.onPause();
        Globals.onPause(this);

        mapWidget.onPause();

        super.onPause();
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
