package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
	private final Map<GeoNode, View> toDisplay = new HashMap<>(); //Visible POIs
	private Map<Long, Integer> routeNumbers = Collections.emptyMap();
	private final ViewGroup container;
	private Vector2d viewSize = new Vector2d(0, 0);
	private Vector2d containerSize = new Vector2d(0, 0);

	public AugmentedRealityViewManager(ViewGroup container) {
		this.container = container;
	}

	/**
	 * Size of the visible area, which matches the camera view.
	 */
	public Vector2d getViewSize() {
		return viewSize;
	}

	/**
	 * Size of the container, which is larger than the visible area and centred on it.
	 */
	public Vector2d getContainerSize() {
		return containerSize;
	}

	public void postInit() {
		viewSize = new Vector2d(container.getMeasuredWidth(), container.getMeasuredHeight());

		// The container is rotated to follow the device roll, so it is grown into a square as
		// wide as the view diagonal and centred on the view, to keep covering it at any angle.
		int side = (int) Math.ceil(Math.hypot(viewSize.x, viewSize.y));
		int horizontalOffset = (int) Math.ceil((side - viewSize.x) / 2);
		int verticalOffset = (int) Math.ceil((side - viewSize.y) / 2);

		RelativeLayout.LayoutParams params =
				(RelativeLayout.LayoutParams) container.getLayoutParams();
		params.setMargins(-horizontalOffset, -verticalOffset, -horizontalOffset, -verticalOffset);
		params.width = side;
		params.height = side;
		container.setLayoutParams(params);

		containerSize = new Vector2d(side, side);
	}

	private void deleteViewFromContainer(View button) {
		container.removeView(button);
	}

	private void addViewToContainer(View button) {
		container.addView(button);
	}


	private View addViewElementFromTemplate(AppCompatActivity parent, final GeoNode poi) {
		LayoutInflater inflater =
				(LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View newViewElement = inflater.inflate(R.layout.button_topo_display, container, false);

		// Only POIs that pass the display filters reach this point, so they are all interactive.
		// The top of a route, and the start of a route relation, open the route, see
		// NodeDialogBuilder#showNodeInfoDialog.
		newViewElement.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				NodeDialogBuilder.showNodeInfoDialog(parent, poi);
			}
		});
		setIcon((ImageButton) newViewElement, createIcon(parent, poi));

		return newViewElement;
	}

	// The button is laid out at the icon's own size, and only scaled when drawn.
	private static void setIcon(ImageButton button, Drawable icon) {
		button.setImageDrawable(icon);
		ViewGroup.LayoutParams params = button.getLayoutParams();
		params.width = icon.getIntrinsicWidth();
		params.height = icon.getIntrinsicHeight();
		button.setLayoutParams(params);
	}

	private Drawable createIcon(AppCompatActivity parent, GeoNode poi) {
		DisplayableGeoNode displayable = new DisplayableGeoNode(poi);
		displayable.setRouteNumber(getRouteNumber(routeNumbers, poi));
		return new PoiMarkerDrawable(parent, displayable,
				DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE, PoiMarkerDrawable.PinShape.slim)
				.getDrawable();
	}

	/**
	 * Sets the crag route numbers shown on the pins, keyed by node ID, and redraws the pins on
	 * display whose number changed.
	 */
	public void setRouteNumbers(AppCompatActivity parent, Map<Long, Integer> routeNumbers) {
		Map<Long, Integer> previous = this.routeNumbers;
		this.routeNumbers = routeNumbers;
		for (Map.Entry<GeoNode, View> entry : toDisplay.entrySet()) {
			GeoNode poi = entry.getKey();
			if (getRouteNumber(previous, poi) != getRouteNumber(routeNumbers, poi)) {
				setIcon((ImageButton) entry.getValue(), createIcon(parent, poi));
			}
		}
	}

	// The crag and area pins are relations, whose IDs may match a node's.
	private static int getRouteNumber(Map<Long, Integer> routeNumbers, GeoNode poi) {
		Integer routeNumber = routeNumbers.get(poi.osmID);
		String osmType = poi.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE,
				OsmEntity.EntityOsmType.node.name());
		return routeNumber != null && OsmEntity.EntityOsmType.node.name().equals(osmType)
				? routeNumber : 0;
	}

	private void updateViewElement(View pButton, GeoNode poi, float stackOrder) {
		Drawable icon = ((ImageButton) pButton).getDrawable();
		Vector2d iconSize = new Vector2d(icon.getIntrinsicWidth(), icon.getIntrinsicHeight());

		Vector4d pos = AugmentedRealityUtils.getXYPosition(poi.difDegAngle, poi.elevationDegAngle,
				-Globals.virtualCamera.degPitch,
				0, Globals.virtualCamera.screenRotation, iconSize,
				Globals.virtualCamera.angleOfViewDeg, viewSize, containerSize);

		// The position centres the icon on the POI, but as on the map, it is the pin tip at the
		// bottom of the icon that points at it, so the POI sits on the terrain.
		pButton.setX((float) pos.x);
		pButton.setY((float) (pos.y - iconSize.y / 2));
		// Scaling and turning around the tip keeps it on the POI.
		pButton.setPivotX((float) iconSize.x / 2);
		pButton.setPivotY((float) iconSize.y);
		float scale = calculateScale(poi.distanceMeters);
		pButton.setScaleX(scale);
		pButton.setScaleY(scale);
		pButton.setRotation((float) pos.w);

		// Z decides both draw order and touch dispatch order, so the caller's stacking is kept
		// without reordering the container's children on every frame.
		pButton.setZ(stackOrder);
	}

	/**
	 * The scale of a pin at the given distance. The distance gives the height of a crag pin, and
	 * the taller route pins are scaled alike, so that they keep their size relative to it.
	 */
	private static float calculateScale(double distance) {
		double heightDp = AugmentedRealityUtils.perspectiveSize(distance,
				UIConstants.UI_NEAR_PIN_DISTANCE_METERS, UIConstants.UI_NEAR_PIN_HEIGHT_DP,
				(int) Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue,
				UIConstants.UI_FAR_PIN_HEIGHT_DP);
		return (float) (Globals.convertDpToPixel(heightDp)
				/ MarkerUtils.IconType.poiRouteIcon.getIconPxHeight());
	}

	public void removePOIFromView(GeoNode poi) {
		View button = toDisplay.remove(poi);
		if (button != null) {
			deleteViewFromContainer(button);
		}
	}

	public void addOrUpdatePOIToView(AppCompatActivity parent, GeoNode poi, float stackOrder) {
		View button = toDisplay.get(poi);
		if (button == null) {
			button = addViewElementFromTemplate(parent, poi);
			toDisplay.put(poi, button);
			addViewToContainer(button);
		}

		updateViewElement(button, poi, stackOrder);
	}

	public View getContainer() {
		return container;
	}

	public void setRotation(float w) {
		container.setRotation(w);
	}
}
