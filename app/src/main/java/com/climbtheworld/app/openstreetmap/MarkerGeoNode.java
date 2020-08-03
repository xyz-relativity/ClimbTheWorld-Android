package com.climbtheworld.app.openstreetmap;

import android.graphics.Color;
import android.graphics.drawable.Drawable;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.dialogs.NodeDialogBuilder;
import com.climbtheworld.app.utils.marker.LazyDrawable;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import androidx.appcompat.app.AppCompatActivity;

public class MarkerGeoNode implements MapViewWidget.MapMarkerElement {
    public static final int CLUSTER_CRAG_COLOR = Color.parseColor("#ff00aaaa");
    public static final int CLUSTER_ARTIFICIAL_COLOR = Color.parseColor("#ffaa00aa");
    public static final int CLUSTER_ROUTE_COLOR = Color.parseColor("#ffaaaa00");
    public static final int CLUSTER_DEFAULT_COLOR = Color.parseColor("#ff0088ff");
    public static final int POI_DEFAULT_COLOR = Color.parseColor("#ffeeeeee");
    public static final int POI_ICON_ALPHA_VISIBLE = 220;
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
        return new LazyDrawable(parent, geoNode, alpha, Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
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
