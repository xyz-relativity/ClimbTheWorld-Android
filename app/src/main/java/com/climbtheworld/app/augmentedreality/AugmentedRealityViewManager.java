package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
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
import java.util.Objects;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
	private final Map<GeoNode, View> toDisplay = new HashMap<>(); //Visible POIs
	private Map<Long, Integer> routeNumbers = Collections.emptyMap();
	// The node whose pin is drawn see-through, and lets taps through, null for none, see
	// setSeeThrough.
	private Long seeThroughNodeId;
	private final ViewGroup container;
	// Where the observer is and looks.
	private final VirtualCamera camera;
	private Vector2d viewSize = new Vector2d(0, 0);
	private Vector2d containerSize = new Vector2d(0, 0);

	public AugmentedRealityViewManager(ViewGroup container, VirtualCamera camera) {
		this.container = container;
		this.camera = camera;
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

	/**
	 * Sizes the container to the view it is in, its parent. Again whenever the view's size
	 * changes, as the container no longer follows it once sized.
	 */
	public void postInit() {
		View view = (View) container.getParent();
		viewSize = new Vector2d(view.getWidth(), view.getHeight());

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
		style(parent, newViewElement, poi);

		return newViewElement;
	}

	/**
	 * Gives a pin its icon, and whether it takes taps: the see-through pin lets them through, to
	 * what is under it.
	 */
	private void style(AppCompatActivity parent, View pin, GeoNode poi) {
		setIcon((ImageButton) pin, createIcon(parent, poi));
		pin.setClickable(!isSeeThrough(poi));
	}

	private boolean isSeeThrough(GeoNode poi) {
		return seeThroughNodeId != null && seeThroughNodeId == poi.getID();
	}

	/**
	 * Draws the pin of a node see-through, and lets taps through it, as the pin of the route being
	 * aligned on is drawn where the A.R. has it until it is dropped, see
	 * AugmentedRealityScene.showOnly, and redraws the pins on display this changes.
	 *
	 * @param nodeId null for none
	 */
	public void setSeeThrough(AppCompatActivity parent, Long nodeId) {
		Long previous = seeThroughNodeId;
		if (Objects.equals(previous, nodeId)) {
			return;
		}
		seeThroughNodeId = nodeId;
		for (Map.Entry<GeoNode, View> entry : toDisplay.entrySet()) {
			long id = entry.getKey().getID();
			if ((previous != null && previous == id) || (nodeId != null && nodeId == id)) {
				style(parent, entry.getValue(), entry.getKey());
			}
		}
	}

	/**
	 * @return the view of the see-through pin, null while it is not on display; its tip is on the
	 * node, see placePin
	 */
	public View getSeeThroughPin() {
		if (seeThroughNodeId == null) {
			return null;
		}
		for (Map.Entry<GeoNode, View> entry : toDisplay.entrySet()) {
			if (entry.getKey().getID() == seeThroughNodeId) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Gives an A.R. pin its icon. The pin is laid out at the icon's own size, and only scaled
	 * when drawn, see placePin.
	 */
	public static void setIcon(ImageView pin, Drawable icon) {
		pin.setImageDrawable(icon);
		ViewGroup.LayoutParams params = pin.getLayoutParams();
		params.width = icon.getIntrinsicWidth();
		params.height = icon.getIntrinsicHeight();
		pin.setLayoutParams(params);
	}

	private Drawable createIcon(AppCompatActivity parent, GeoNode poi) {
		DisplayableGeoNode displayable = new DisplayableGeoNode(poi);
		displayable.setRouteNumber(getRouteNumber(routeNumbers, poi));
		return createIcon(parent, displayable, isSeeThrough(poi)
				? DisplayableGeoNode.POI_ICON_ALPHA_SEE_THROUGH
				: DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);
	}

	/**
	 * The icon of a pin, as the A.R. views draw them.
	 */
	public static Drawable createIcon(AppCompatActivity parent, DisplayableGeoNode poi) {
		return createIcon(parent, poi, DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);
	}

	/**
	 * The icon of a pin, as the A.R. views draw them, with an alpha of its own: see-through for
	 * the route being aligned on, where the A.R. has it until it is dropped.
	 */
	public static Drawable createIcon(AppCompatActivity parent, DisplayableGeoNode poi,
	                                  int alpha) {
		return new PoiMarkerDrawable(parent, poi, alpha, PoiMarkerDrawable.PinShape.slim)
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
				-camera.degPitch,
				0, camera.screenRotation, iconSize,
				camera.angleOfViewDeg, viewSize, containerSize);

		// The position centres the icon on the POI.
		placePin(pButton, pos.x + iconSize.x / 2, pos.y + iconSize.y / 2, poi.distanceMeters,
				(float) pos.w);

		// Z decides both draw order and touch dispatch order, so the caller's stacking is kept
		// without reordering the container's children on every frame.
		pButton.setZ(stackOrder);
	}

	/**
	 * Puts an A.R. pin over a point of the view. As on the map, it is the pin tip at the bottom of
	 * the icon that points at it, so the POI sits on the terrain.
	 *
	 * @param distance how far the POI is, which sets the pin's size
	 * @param rotation clockwise, in degrees
	 */
	public static void placePin(View pin, double x, double y, double distance, float rotation) {
		Drawable icon = ((ImageView) pin).getDrawable();
		int width = icon.getIntrinsicWidth();
		int height = icon.getIntrinsicHeight();
		pin.setX((float) (x - width / 2.0));
		pin.setY((float) (y - height));
		// Scaling and turning around the tip keeps it on the POI.
		pin.setPivotX(width / 2f);
		pin.setPivotY(height);
		float scale = calculateScale(distance);
		pin.setScaleX(scale);
		pin.setScaleY(scale);
		pin.setRotation(rotation);
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
