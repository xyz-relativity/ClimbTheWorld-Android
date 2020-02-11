package com.climbtheworld.app.openstreetmap;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.util.GeoPoint;

public class MarkerGeoNode implements MapViewWidget.MapMarkerElement {
    private static final int CLUSTER_CRAG_COLOR = Color.parseColor("#ff00aaaa");
    private static final int CLUSTER_ARTIFICIAL_COLOR = Color.parseColor("#ffaa00aa");
    private static final int CLUSTER_ROUTE_COLOR = Color.parseColor("#ffaaaa00");
    private static final int CLUSTER_DEFAULT_COLOR = Color.parseColor("#ff0088ff");
    static int POI_DEFAULT_COLOR = Color.parseColor("#ffeeeeee");

    private int alpha = POI_ICON_VISIBLE_ALPHA;

    public static final double POI_ICON_SIZE_MULTIPLIER = 0.6;
    private static final int POI_ICON_VISIBLE_ALPHA = 240;
    private static final int POI_ICON_HIDDEN_ALPHA = 50;


    public final GeoNode geoNode;
    public MarkerGeoNode(GeoNode geoNode) {
        this.geoNode = geoNode;
    }

    @Override
    public GeoPoint getGeoPoint() {
        return Globals.poiToGeoPoint(geoNode);
    }

    @Override
    public GeoNode getGeoNode() {
        return geoNode;
    }

    @Override
    public Drawable getIcon(AppCompatActivity parent) {
        return MarkerUtils.getPoiIcon(parent, geoNode, POI_ICON_SIZE_MULTIPLIER, alpha);
    }

    @Override
    public int getOverlayPriority() {
        switch (geoNode.getNodeType()) {
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
        return new BitmapDrawable(parent.getResources(),MarkerUtils.getBitmap((VectorDrawable)nodeIcon, originalW, originalH, POI_ICON_SIZE_MULTIPLIER));
    }

    @Override
    public void showOnClickDialog(AppCompatActivity parent) {
        NodeDialogBuilder.showNodeInfoDialog(parent, geoNode);
    }

    @Override
    public GeoNode getMarkerData() {
        return geoNode;
    }

    @Override
    public void setVisibility(boolean visible) {
        if (visible) {
            alpha = POI_ICON_VISIBLE_ALPHA;
        } else {
            alpha = POI_ICON_HIDDEN_ALPHA;
        }
    }
}
