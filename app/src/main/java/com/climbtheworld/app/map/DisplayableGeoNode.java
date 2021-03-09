package com.climbtheworld.app.map;

import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

public class DisplayableGeoNode {
	public static final int CLUSTER_DEFAULT_COLOR = Color.parseColor("#ff0088ff");
	public static final int POI_DEFAULT_COLOR = Color.parseColor("#ffeeeeee");
	public static final int POI_ICON_ALPHA_VISIBLE = 220;
	public static final int POI_ICON_ALPHA_HIDDEN = 30;
	public static final int POI_ICON_DP_SIZE = 76;
	public static final int CLUSTER_ICON_DP_SIZE = 76;

	private int alpha = POI_ICON_ALPHA_VISIBLE;

	private boolean showPoiInfoDialog;
	public final GeoNode geoNode;

	public DisplayableGeoNode(GeoNode geoNode) {
		this(geoNode, false);
	}

	public DisplayableGeoNode(GeoNode geoNode, boolean showPoiInfoDialog) {
		this.geoNode = geoNode;
		this.showPoiInfoDialog = showPoiInfoDialog;
	}

	public boolean isShowPoiInfoDialog() {
		return showPoiInfoDialog;
	}

	private boolean setShowPoiInfoDialog(boolean showPoiInfoDialog) {
		boolean oldVisibility = this.showPoiInfoDialog;
		this.showPoiInfoDialog = showPoiInfoDialog;

		return oldVisibility != this.showPoiInfoDialog;
	}

	public boolean setGhost(boolean isGhost) {
		boolean visibilityChanged = setVisibility(!isGhost);
		boolean dialogChanged = setShowPoiInfoDialog(!isGhost);
		return visibilityChanged || dialogChanged;
	}

	public GeoNode getGeoNode() {
		return geoNode;
	}

	public void showOnClickDialog(AppCompatActivity parent) {
		NodeDialogBuilder.showNodeInfoDialog(parent, geoNode);
	}

	public boolean setVisibility(boolean visible) {
		int oldAlpha = alpha;
		if (visible) {
			alpha = POI_ICON_ALPHA_VISIBLE;
		} else {
			alpha = POI_ICON_ALPHA_HIDDEN;
		}

		return oldAlpha != alpha;
	}

	public boolean isVisible() {
		return alpha == POI_ICON_ALPHA_VISIBLE;
	}

	public int getAlpha() {
		return alpha;
	}

	public boolean isGhost() {
		return (alpha == POI_ICON_ALPHA_HIDDEN && !showPoiInfoDialog);
	}
}
