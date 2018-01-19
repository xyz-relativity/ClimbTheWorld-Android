package com.ar.openClimbAR;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.ar.openClimbAR.utils.Constants;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

public class ViewMapActivity extends AppCompatActivity {

    private MapView osmMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        osmMap = findViewById(R.id.openMapView);

        //init osm map
        osmMap.setBuiltInZoomControls(true);
        osmMap.setTilesScaledToDpi(true);
        osmMap.setMultiTouchControls(true);
        osmMap.setTileSource(TileSourceFactory.OpenTopo);
        osmMap.getController().setZoom(Constants.MAP_ZOOM_LEVEL - 6);
    }
}
