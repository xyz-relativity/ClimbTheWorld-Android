package com.climbtheworld.app.openstreetmap;

import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.appcompat.app.AppCompatActivity;

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
    public static final int POI_DEFAULT_COLOR = Color.parseColor("#ffeeeeee");
    public static final int POI_ICON_ALPHA_VISIBLE = 250;
    public static final int POI_ICON_ALPHA_HIDDEN = 30;
    public static final int POI_ICON_DP_SIZE = 70;

    private int alpha = POI_ICON_ALPHA_VISIBLE;

    private boolean showPoiInfoDialog = true;
    public final GeoNode geoNode;

    public MarkerGeoNode(GeoNode geoNode) {
        this(geoNode, true);
    }

    public MarkerGeoNode(GeoNode geoNode, boolean showPoiInfoDialog) {
        this.geoNode = geoNode;
        this.showPoiInfoDialog = showPoiInfoDialog;
    }

    public boolean isShowPoiInfoDialog() {
        return showPoiInfoDialog;
    }

    public void setShowPoiInfoDialog(boolean showPoiInfoDialog) {
        this.showPoiInfoDialog = showPoiInfoDialog;
    }

    @Override
    public void setGhost(boolean isGhost) {
        setVisibility(isGhost);
        setShowPoiInfoDialog(isGhost);
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
        return MarkerUtils.getPoiIcon(parent, geoNode, alpha);
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
        return MarkerUtils.getClusterIcon(parent, getOverlayColor(getOverlayPriority()), 255);
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
            alpha = POI_ICON_ALPHA_VISIBLE;
        } else {
            alpha = POI_ICON_ALPHA_HIDDEN;
        }
    }

    @Override
    public boolean isVisible() {
        return alpha == POI_ICON_ALPHA_VISIBLE;
    }
}
