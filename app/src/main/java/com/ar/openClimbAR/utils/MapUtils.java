package com.ar.openClimbAR.utils;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.tools.PointOfInterest;
import com.ar.openClimbAR.tools.PointOfInterestDialogBuilder;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

/**
 * Created by xyz on 1/19/18.
 */

public class MapUtils {
    private MapUtils() {
        //hide constructor
    }

    public static Marker initMyLocationMarkers(MapView mapView, FolderOverlay myMarkersFolder) {
        List<Overlay> list = myMarkersFolder.getItems();

        Drawable nodeIcon = mapView.getContext().getResources().getDrawable(R.drawable.direction_arrow);
        nodeIcon.mutate(); //allow different effects for each marker.

        Marker locationMarker = new Marker(mapView);
        locationMarker.setAnchor(0.5f, 0.5f);
        locationMarker.setIcon(nodeIcon);
        locationMarker.setImage(nodeIcon);
        locationMarker.setInfoWindow(null);

        //put into FolderOverlay list
        list.add(locationMarker);

        myMarkersFolder.closeAllInfoWindows();

        mapView.getOverlays().clear();
        mapView.getOverlays().add(myMarkersFolder);
        mapView.invalidate();

        return locationMarker;
    }

    public static void addMapMarker(final PointOfInterest poi, MapView mapView, FolderOverlay myMarkersFolder) {
        List<Overlay> list = myMarkersFolder.getItems();

        Drawable nodeIcon = mapView.getContext().getResources().getDrawable(R.drawable.marker_default);
        nodeIcon.mutate(); //allow different effects for each marker.

        float remapGradeScale = ArUtils.remapScale(0f,
                GradeConverter.getConverter().maxGrades,
                0f,
                1f,
                poi.getLevelId());
        nodeIcon.setTintList(ColorStateList.valueOf(android.graphics.Color.HSVToColor(new float[]{(float)remapGradeScale*120f,1f,1f})));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

        Marker nodeMarker = new Marker(mapView);
        nodeMarker.setAnchor(0.5f, 1f);
        nodeMarker.setPosition(new GeoPoint(poi.decimalLatitude, poi.decimalLongitude));
        nodeMarker.setIcon(nodeIcon);
        nodeMarker.setTitle(GradeConverter.getConverter().getGradeFromOrder("UIAA", poi.getLevelId()) +" (UIAA)");
        nodeMarker.setSubDescription(poi.name);
        nodeMarker.setImage(nodeIcon);
        nodeMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
//                marker.showInfoWindow();
                PointOfInterestDialogBuilder.buildDialog(mapView.getContext(), poi, GlobalVariables.observer).show();
                return true;
            }
        });

        //put into FolderOverlay list
        list.add(nodeMarker);

        myMarkersFolder.closeAllInfoWindows();

        mapView.getOverlays().clear();
        mapView.getOverlays().add(myMarkersFolder);
        mapView.invalidate();
    }
}
