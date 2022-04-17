package com.climbtheworld.app.map.marker;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.lang.ref.WeakReference;

public class GeoNodeMapMarker extends Marker {
	private final WeakReference<AppCompatActivity> parentRef;
	private final PoiMarkerDrawable poiIcon;
	private final DisplayableGeoNode poi;

	public GeoNodeMapMarker(AppCompatActivity parent, MapView mapView, DisplayableGeoNode poi) {
		super(mapView);

		this.poi = poi;
		this.parentRef = new WeakReference<>(parent);
		poiIcon = new PoiMarkerDrawable(parent, mapView, poi, Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

		this.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		this.setPosition(Globals.geoNodeToGeoPoint(poi.getGeoNode()));
		this.setIcon(poiIcon);

		updateDisplayState();
	}

	public DisplayableGeoNode getPoi() {
		return poi;
	}

	public void applyFilters() {
		setGhost(!NodeDisplayFilters.matchFilters(Configs.instance(parentRef), this.getGeoNode()));
	}

	public void setGhost(boolean isGhost) {
		poi.setGhost(isGhost);
		updateDisplayState();
	}

	private void updateDisplayState() {
		this.setAlpha(poi.getAlpha() / 255f);

		if (poi.isShowPoiInfoDialog()) {
			this.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
				@Override
				public boolean onMarkerClick(Marker marker, MapView mapView) {
					poi.showOnClickDialog(parentRef.get());
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
	}

	public GeoNode getGeoNode() {
		return poi.geoNode;
	}
}
