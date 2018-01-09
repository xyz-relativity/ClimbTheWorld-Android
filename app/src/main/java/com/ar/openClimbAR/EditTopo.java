package com.ar.openClimbAR;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.utils.Constants;

import org.json.JSONException;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

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
        osmMap.getController().setCenter(poi.geoPoint);
    }
}
