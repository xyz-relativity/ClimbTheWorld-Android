package com.climbtheworld.app.activitys;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.climbtheworld.app.R;
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
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EditTopoActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, AdapterView.OnItemSelectedListener {
    private GeoNode poi;
    private Long poiID;
    private MapViewWidget mapWidget;
    private LocationHandler locationHandler;
    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private Spinner dropdown;
    private EditText editTopoName;
    private EditText editElevation;
    private EditText editLength;
    private EditText editDescription;
    private EditText editLatitude;
    private EditText editLongitude;
    private CheckBox checkBoxProtection;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));
        this.editTopoName = findViewById(R.id.editTopoName);
        this.editElevation = findViewById(R.id.editElevation);
        this.editLength = findViewById(R.id.editLength);
        this.editDescription = findViewById(R.id.editDescription);
        this.editLatitude = findViewById(R.id.editLatitude);
        this.editLongitude = findViewById(R.id.editLongitude);
        this.checkBoxProtection = findViewById(R.id.bolted);
        this.dropdown = findViewById(R.id.gradeSpinner);

        //location
        locationHandler = new LocationHandler((LocationManager) getSystemService(Context.LOCATION_SERVICE), EditTopoActivity.this, this);
        locationHandler.addListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorListener = new SensorListener();
        sensorListener.addListener(this, compass);

        intent = getIntent();
        AsyncTask task = new BdLoad().execute(intent);
        try {
            poi = (GeoNode)task.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return;
        }
        poiID = poi.getID();

        Map<Long, GeoNode> poiMap = new HashMap<>();
        poiMap.put(poiID, poi);

        mapWidget = new MapViewWidget(this, findViewById(R.id.mapViewContainer), poiMap);
        mapWidget.setShowPoiInfoDialog(false);
        mapWidget.setAllowAutoCenter(false);
        mapWidget.addTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < Constants.ON_TAP_DELAY_MS)) {
                    GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels((int) motionEvent.getX(), (int) motionEvent.getY());

                    poi.updatePOILocation(gp.getLatitude(), gp.getLongitude(), poi.elevationMeters);
                    updateMapMarker();

                    return true;
                }
                return false;
            }
        });

        mapWidget.getOsmMap().getController().setCenter(Globals.poiToGeoPoint(poi));
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

        ((TextView)findViewById(R.id.grading)).setText(getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));

        dropdown.setOnItemSelectedListener(this);
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(poi.getLevelId());

        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null) {
                styleCheckBox.setChecked(true);
            }
        }

        checkBoxProtection.setChecked(poi.isBolted());
    }

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        poi.setLevelFromID(pos);
        updateMapMarker();
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }


    public void updatePoi() {
        poi.updatePOILocation(Double.parseDouble(editLatitude.getText().toString()),
                Double.parseDouble(editLongitude.getText().toString()),
                Double.parseDouble(editElevation.getText().toString()));

        poi.setName(editTopoName.getText().toString());
        poi.setDescription(editDescription.getText().toString());
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
        poi.setLevelFromID(dropdown.getSelectedItemPosition());
        poi.setBolted(checkBoxProtection.isChecked());
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ButtonCancel:
                finish();
                break;

            case R.id.ButtonSave:
                updatePoi();
                poi.updateDate = System.currentTimeMillis();
                poi.localUpdateState = GeoNode.TO_UPDATE_STATE;
                new BdPush().execute(poi);
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
                                AsyncTask pushData = new BdPush().execute(poi);
                                try {
                                    pushData.get(2, TimeUnit.SECONDS);
                                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                    DialogBuilder.showErrorDialog(EditTopoActivity.this, e.getMessage(), null);
                                }
                                finish();
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

        mapWidget.invalidate();
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationHandler.onResume();
        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL);

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        locationHandler.onPause();
        sensorManager.unregisterListener(sensorListener);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    public void onCompassButtonClick (View v) {
        DialogBuilder.buildObserverInfoDialog(v);
    }

    private static class BdLoad extends AsyncTask<Intent, Void, GeoNode> {
        @Override
        protected GeoNode doInBackground(Intent... intents) {
            Intent intent = intents[0];

            long poiID = intent.getLongExtra("poiID", 0);
            if (poiID == 0) {
                GeoNode tmpPoi = new GeoNode(intent.getDoubleExtra("poiLat", Globals.virtualCamera.decimalLatitude),
                        intent.getDoubleExtra("poiLon", Globals.virtualCamera.decimalLongitude),
                        Globals.virtualCamera.elevationMeters);

                poiID = Globals.appDB.nodeDao().getSmallestId();
                if (poiID >= 0) {
                    poiID = -1l;
                } else {
                    poiID -= 1;
                }
                tmpPoi.osmID = poiID;

                return tmpPoi;
            } else {
                return Globals.appDB.nodeDao().loadNode(poiID);
            }
        }
    }

    private static class BdPush extends AsyncTask<GeoNode, Void, Void> {
        @Override
        protected Void doInBackground(GeoNode... nodes) {
            Globals.appDB.nodeDao().insertNodesWithReplace(nodes);
            return null;
        }
    }
}
