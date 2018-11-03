package com.climbtheworld.app.osm;

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
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.util.GeoPoint;

public class MarkerGeoNode implements MapViewWidget.MapMarkerElement {
    public static int CLUSTER_CRAG_COLOR = Color.parseColor("#ff00aaaa");
    public static int CLUSTER_ARTIFICIAL_COLOR = Color.parseColor("#ffaa00aa");
    public static int CLUSTER_ROUTE_COLOR = Color.parseColor("#ffaaaa00");
    public static int CLUSTER_DEFAULT_COLOR = Color.parseColor("#ff888888");
    public static int POI_DEFAULT_COLOR = Color.parseColor("#ffbbbbbb");

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
        return new BitmapDrawable(parent.getResources(), MarkerUtils.getPoiIcon(parent, geoNode, Constants.POI_ICON_SIZE_MULTIPLIER));
    }

    @Override
    public int getOverlayPriority() {
        switch (geoNode.nodeType) {
//            case crag:
//                return 3;
//            case artificial:
//                return 2;
//            case route:
//                return 1;
            default:
                return 0;
        }
    }

    private int getOverlayColor(int priority) {
        switch (priority) {
            case 3:
                return CLUSTER_CRAG_COLOR;
            case 2:
                return CLUSTER_ARTIFICIAL_COLOR;
            case 1:
                return CLUSTER_ROUTE_COLOR;
            default:
                return CLUSTER_DEFAULT_COLOR;
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
        return new BitmapDrawable(parent.getResources(),MarkerUtils.getBitmap((VectorDrawable)nodeIcon, originalW, originalH, Constants.POI_ICON_SIZE_MULTIPLIER));
    }

    @Override
    public AlertDialog getOnClickDialog(AppCompatActivity parent) {
        return DialogBuilder.buildNodeInfoDialog(parent, geoNode);
    }
}
