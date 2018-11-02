package com.climbtheworld.app.storage.views;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.support.v7.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.MappingUtils;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.util.GeoPoint;

public class MarkerGeoNode implements MapViewWidget.MapMarkerElement {
    public final GeoNode geoNode;
    public MarkerGeoNode(GeoNode geoNode) {
        this.geoNode = geoNode;
    }

    @Override
    public GeoPoint getGeoPoint() {
        return Globals.poiToGeoPoint(geoNode);
    }

    @Override
    public Drawable getIcon(AppCompatActivity parent) {
        return new BitmapDrawable(parent.getResources(), MappingUtils.getPoiIcon(parent, geoNode, Constants.POI_ICON_SIZE_MULTIPLIER));
    }

    @Override
    public int getOverlayPriority() {
        switch (geoNode.nodeType) {
            case crag:
                return 3;
            case artificial:
                return 2;
            case route:
                return 1;
            default:
                return 0;
        }
    }

    private int getOverlayColor(int priority) {
        switch (priority) {
            case 3:
                return Color.parseColor("#ff00aaaa");
            case 2:
                return Color.parseColor("#ffaa00aa");
            case 1:
                return Color.parseColor("#ffaaaa00");
            default:
                return Color.parseColor("#ffaaaaaa");
        }
    }

    @Override
    public Drawable getOverlayIcon(AppCompatActivity parent) {
        int originalW = 300;
        int originalH = 300;

        Drawable nodeIcon = parent.getResources().getDrawable(R.drawable.ic_clusters);
        nodeIcon.mutate(); //allow different effects for each marker.
        nodeIcon.setTintList(ColorStateList.valueOf(getOverlayColor(getOverlayPriority())));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
        return new BitmapDrawable(parent.getResources(),MappingUtils.getBitmap((VectorDrawable)nodeIcon, originalW, originalH, Constants.POI_ICON_SIZE_MULTIPLIER));
    }

    @Override
    public AlertDialog getOnClickDialog(AppCompatActivity parent) {
        return DialogBuilder.buildNodeInfoDialog(parent, geoNode);
    }
}
