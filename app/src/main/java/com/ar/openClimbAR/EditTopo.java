package com.ar.openClimbAR;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.utils.Constants;

import org.json.JSONException;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.ArrayList;

import static com.ar.openClimbAR.tools.PointOfInterest.POIType.climbing;

public class EditTopo extends AppCompatActivity {
    private MapView osmMap;
    private PointOfInterest poi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topo);

        osmMap = findViewById(R.id.openMapView);

        Intent intent = getIntent();
        try {
            poi = new PointOfInterest(climbing, intent.getStringExtra("poiJSON"));
        } catch (JSONException e) {
            e.printStackTrace();
            finish();
        }

        //init osm map
        osmMap.setBuiltInZoomControls(false);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.OpenTopo);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL);
        osmMap.getController().setCenter(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));

        ((EditText)findViewById(R.id.editTopoName)).setText(poi.name);
        ((EditText)findViewById(R.id.editLatitude)).setText(Float.toString(poi.decimalLatitude));
        ((EditText)findViewById(R.id.editLongitude)).setText(Float.toString(poi.decimalLongitude));
        ((EditText)findViewById(R.id.editAltitude)).setText(Float.toString(poi.altitudeMeters));
        ((EditText)findViewById(R.id.editLength)).setText(Float.toString(poi.getLengthMeters()));
        ((EditText)findViewById(R.id.editDescription)).setText(poi.getDescription());

        ((TextView)findViewById(R.id.grading)).setText(getResources().getString(R.string.grade) + "(" + Constants.DISPLAY_SYSTEM + ")");
        Spinner dropdown = findViewById(R.id.gradeSpinner);
        ArrayList<String> allGrades = GradeConverter.getConverter().getAllGrades(Constants.DISPLAY_SYSTEM);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(poi.getLevelId());

        for (PointOfInterest.climbingStyle style :poi.getClimbingStyles())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            ((CheckBox)findViewById(id)).setChecked(true);
        }
    }
}
