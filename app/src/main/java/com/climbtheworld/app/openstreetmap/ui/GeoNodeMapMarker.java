package com.climbtheworld.app.openstreetmap.ui;

import com.climbtheworld.app.storage.database.GeoNode;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

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
