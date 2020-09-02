package com.climbtheworld.app.map.marker;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.NodeDisplayFilters;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import androidx.appcompat.app.AppCompatActivity;

public class GeoNodeMapMarker extends Marker {
    private final AppCompatActivity parent;
    private final PoiMarkerDrawable poiIcon;
    private DisplayableGeoNode poi;

    public GeoNodeMapMarker(AppCompatActivity parent, MapView mapView, DisplayableGeoNode poi) {
        super(mapView);

        this.poi = poi;
        this.parent = parent;
        poiIcon = new PoiMarkerDrawable(parent, mapView, poi, Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        updateAlpha(poi.getAlpha());
        this.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        this.setPosition(Globals.poiToGeoPoint(poi.getGeoNode()));
        this.setIcon(poiIcon);
    }

    public DisplayableGeoNode getPoi() {
        return poi;
    }

    public void applyFilters() {
        setGhost(!NodeDisplayFilters.passFilter(Configs.instance(parent), this.getGeoNode()));
    }

    private void setGhost(boolean isGhost) {
        if (poi.setGhost(isGhost)) {
            if (poi.isShowPoiInfoDialog()) {
                this.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker, MapView mapView) {
                        poi.showOnClickDialog(parent);
                        return true;
                    }
                });
            } else {
                this.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker, MapView mapView) {
                        return false;
                    }
                });
            }
            updateAlpha(poi.getAlpha());
        }
    }

    private void updateAlpha(int alpha) {
        this.setAlpha(alpha / 255f);
    }

    public GeoNode getGeoNode() {
        return poi.geoNode;
    }
}