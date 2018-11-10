package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.osm.MarkerUtils;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationHandler;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ViewUtils;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.json.JSONException;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import needle.UiRelatedTask;

public class EditNodeActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, AdapterView.OnItemSelectedListener {
    private GeoNode poi;
    Map<Long, MapViewWidget.MapMarkerElement> poiMap = new ConcurrentHashMap<>();
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private Spinner dropdownGrade;
    private Spinner dropdownType;
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editLength;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private EditText editTopoWebsite;

    private Intent intent;
    private long poiID;

    private final int locationUpdate = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_node);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.editTopoName = findViewById(R.id.editTopoName);
        this.editElevation = findViewById(R.id.editElevation);
        this.editLength = findViewById(R.id.editLength);
        this.editDescription = findViewById(R.id.editDescription);
        this.editLatitude = findViewById(R.id.editLatitude);
        this.editLongitude = findViewById(R.id.editLongitude);
        this.dropdownGrade = findViewById(R.id.gradeSpinner);
        this.dropdownType = findViewById(R.id.spinnerNodeType);
        this.editTopoWebsite = findViewById(R.id.editTextTopoWebsite);
        intent = getIntent();

        poiID = intent.getLongExtra("poiID", 0);

        mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), poiMap);
        mapWidget.setShowPoiInfoDialog(false);
        mapWidget.setMapAutoCenter(false);
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

                    poi.updatePOILocation(gp.getLatitude(), gp.getLongitude(), poi.elevationMeters);
                    updateMapMarker();

                    return true;
                }
                return false;
            }
        });

        //location
        locationHandler = new LocationHandler(EditNodeActivity.this, this, locationUpdate);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);

        Constants.DB_EXECUTOR
                .execute(new UiRelatedTask<GeoNode>() {
                    @Override
                    protected GeoNode doWork() {
                        if (poiID == 0) {
                            GeoNode tmpPoi = new GeoNode(intent.getDoubleExtra("poiLat", Globals.virtualCamera.decimalLatitude),
                                    intent.getDoubleExtra("poiLon", Globals.virtualCamera.decimalLongitude),
                                    Globals.virtualCamera.elevationMeters);
                            tmpPoi.nodeType = GeoNode.NodeTypes.route;

                            long tmpID = Globals.appDB.nodeDao().getSmallestId();
                            if (tmpID >= 0) {
                                tmpID = -1l;
                            } else {
                                tmpID -= 1;
                            }
                            tmpPoi.osmID = tmpID;

                            return tmpPoi;
                        } else {
                            return Globals.appDB.nodeDao().loadNode(poiID);
                        }
                    }

                    @Override
                    protected void thenDoUiRelatedWork(GeoNode result) {
                        poi = result;
                        updateUI();
                    }
                });
    }

    private void loadStyles() {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = poi.getClimbingStyles();

        RadioGroup container = findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(this, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            container.addView(customSwitch);
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        switch (parent.getId()) {
            case R.id.gradeSpinner:
                poi.setLevelFromID(pos);
                break;

            case R.id.spinnerNodeType:
                poi.nodeType = GeoNode.NodeTypes.values()[pos];
                break;
        }
        updatePoi();
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    private void updateUI() {
        poiMap.clear();
        poiMap.put(poi.getID(), new MarkerGeoNode(poi));
        mapWidget.centerOnGoePoint(Globals.poiToGeoPoint(poi));
        updateMapMarker();

        editTopoName.setText(poi.getName());
        editElevation.setText(String.format(Locale.getDefault(), "%.2f", poi.elevationMeters));
        editLength.setText(String.format(Locale.getDefault(), "%.2f", poi.getLengthMeters()));
        editDescription.addTextChangedListener(new TextWatcher() {
            TextView description = findViewById(R.id.description);
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                description.setText(getString(R.string.description_num_characters, editDescription.getText().length()));
                editDescription.setHint(getString(R.string.description_num_characters, editDescription.getText().length()));
            }
        });
        editDescription.setText(poi.getDescription());

        editTopoWebsite.setText(poi.getWebsite());

        ((TextView)findViewById(R.id.grading)).setText(getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));

        dropdownGrade.setOnItemSelectedListener(this);
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        dropdownGrade.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades));
        dropdownGrade.setSelection(poi.getLevelId());

        loadStyles();

        dropdownType.setOnItemSelectedListener(this);
        dropdownType.setAdapter(new MarkerUtils.SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GeoNode.NodeTypes.values(), poi));
        dropdownType.setSelection(Arrays.asList(GeoNode.NodeTypes.values()).indexOf(poi.nodeType));
        if (poiID == 0) {
            dropdownType.performClick();
        }
    }

    public void updatePoi() {
        poi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                Double.parseDouble(editLongitude.getText().toString()),
                Double.parseDouble(editElevation.getText().toString()));

        poi.setName(editTopoName.getText().toString());
        poi.setDescription(editDescription.getText().toString());
        poi.setWebsite(editTopoWebsite.getText().toString());
        poi.setLengthMeters(Double.parseDouble(editLength.getText().toString()));

        List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }
        poi.setClimbingStyles(styles);
        poi.setLevelFromID(dropdownGrade.getSelectedItemPosition());

        dropdownType.setAdapter(new MarkerUtils.SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GeoNode.NodeTypes.values(), poi));
        dropdownType.setSelection(Arrays.asList(GeoNode.NodeTypes.values()).indexOf(poi.nodeType));

        updateMapMarker();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAdvanceEdit:
                Intent newIntent = new Intent(EditNodeActivity.this, EditNodeAdvancedActivity.class);
                newIntent.putExtra("nodeJson", poi.toJSONString());
                startActivity(newIntent);
                break;

            case R.id.ButtonCancel:
                finish();
                break;

            case R.id.ButtonSave:
                updatePoi();
                poi.updateDate = System.currentTimeMillis();
                poi.localUpdateState = GeoNode.TO_UPDATE_STATE;
                Constants.DB_EXECUTOR
                        .execute(new UiRelatedTask<Boolean>() {
                            @Override
                            protected Boolean doWork() {
                                if (poi.osmID < 0 && poi.localUpdateState == GeoNode.TO_DELETE_STATE) {
                                    Globals.appDB.nodeDao().deleteNodes(poi);
                                } else {
                                    Globals.appDB.nodeDao().insertNodesWithReplace(poi);
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
                        .setTitle(getResources().getString(R.string.delete_confirmation ,poi.getName()))
                        .setMessage(R.string.delete_confirmation_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                poi.updateDate = System.currentTimeMillis();
                                poi.localUpdateState = GeoNode.TO_DELETE_STATE;

                                Constants.DB_EXECUTOR
                                        .execute(new UiRelatedTask<Boolean>() {
                                            @Override
                                            protected Boolean doWork() {
                                                if (poi.osmID < 0 && poi.localUpdateState == GeoNode.TO_DELETE_STATE) {
                                                    Globals.appDB.nodeDao().deleteNodes(poi);
                                                } else {
                                                    Globals.appDB.nodeDao().insertNodesWithReplace(poi);
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

    private void updateMapMarker() {
        editLatitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), "%f", poi.decimalLongitude));

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
        DialogBuilder.buildObserverInfoDialog(v);
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            String nodeJson = intent.getStringExtra("nodeJson");
            try {
                poi = new GeoNode(nodeJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            updatePoi();
        }
    }
}
