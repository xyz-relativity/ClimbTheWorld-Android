package com.climbtheworld.app.map;

import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

public class DisplayableGeoNode {
	public static final int CLUSTER_DEFAULT_COLOR = Color.parseColor("#ff0088ff");
	public static final int POI_DEFAULT_COLOR = Color.parseColor("#ffeeeeee");
	// Translucency shared by every convex hull fill, on the map and in the AR view.
	public static final int HULL_FILL_ALPHA = 0x60;
	// Translucent fills of the area and crag convex hulls.
	public static final int AREA_HULL_COLOR = ColorUtils.setAlphaComponent(
			Color.parseColor("#00ffff"), HULL_FILL_ALPHA); //#20cdffd8  #20e6ffed
	public static final int CRAG_HULL_COLOR = ColorUtils.setAlphaComponent(
			Color.parseColor("#ffff00"), HULL_FILL_ALPHA); //#20dbedff  #20f1f8ff
	// Pins of artificial climbing: magenta for gyms, violet for the walls, towers and blocks
	// outdoors. Both stay clear of the red to green of the grades, the crag yellow, the area cyan
	// and the cluster blue.
	public static final int ARTIFICIAL_INDOOR_PIN_COLOR = Color.parseColor("#ffff4fd8");
	public static final int ARTIFICIAL_OUTDOOR_PIN_COLOR = Color.parseColor("#ff9a6bff");
	// Lines on the ground: the outlines of the hulls in the AR view, and the routes mapped as a
	// way, there and on the map. A dark edge along the line keeps it readable over bright rock,
	// sky and map.
	public static final int LINE_ALPHA = 0xe0;
	public static final int LINE_CASING_COLOR = Color.argb(0x80, 0, 0, 0);
	public static final float LINE_WIDTH_DP = 3;
	public static final float LINE_CASING_WIDTH_DP = 5;
	public static final int POI_ICON_ALPHA_VISIBLE = 255;
	public static final int POI_ICON_ALPHA_HIDDEN = 30;
	public static final int CLUSTER_ICON_ALPHA = 200;
	public static final int POI_ICON_DP_SIZE = 76;
	public static final int CLUSTER_ICON_DP_SIZE = 76;
	// White outline drawn around the labels on every marker, so the text stays readable over the
	// marker tint and the map underneath.
	public static final float MARKER_TEXT_OUTLINE_DP = 2;
	public final GeoNode geoNode;
	private int alpha = POI_ICON_ALPHA_VISIBLE;
	// Position of the route in its crag, or 0 when it is not numbered.
	private int routeNumber = 0;

	private boolean showPoiInfoDialog;

	public DisplayableGeoNode(GeoNode geoNode) {
		this(geoNode, false);
	}

	public DisplayableGeoNode(GeoNode geoNode, boolean showPoiInfoDialog) {
		this.geoNode = geoNode;
		this.showPoiInfoDialog = showPoiInfoDialog;
	}

	/**
	 * Pin colour for area and crag POIs: their hull colour, as opaque as the default pin, so the
	 * pin matches its hull.
	 */
	public static int getHullPinColor(OsmEntity.EntityClimbingType type) {
		switch (type) {
			case area:
				return toPinColor(AREA_HULL_COLOR);
			case crag:
				return toPinColor(CRAG_HULL_COLOR);
			default:
				return POI_DEFAULT_COLOR;
		}
	}

	/**
	 * Pin colour for artificial climbing: one for gyms, another for what is outdoors.
	 */
	public static int getArtificialPinColor(GeoNode.ArtificialKind kind) {
		return kind.isIndoor() ? ARTIFICIAL_INDOOR_PIN_COLOR : ARTIFICIAL_OUTDOOR_PIN_COLOR;
	}

	private static int toPinColor(int hullColor) {
		return ColorUtils.setAlphaComponent(hullColor, Color.alpha(POI_DEFAULT_COLOR));
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

	/**
	 * @return the position of the route in its crag, or 0 when it is not numbered; see
	 * ClimbingRouteCounter#numberRoutes. The pin shows it in front of the name.
	 */
	public int getRouteNumber() {
		return routeNumber;
	}

	public void setRouteNumber(int routeNumber) {
		this.routeNumber = routeNumber;
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
