package com.climbtheworld.app.augmentedreality;

import android.content.Context;
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
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xyz on 12/27/17.
 */

public class AugmentedRealityViewManager {
	private final Map<GeoNode, View> toDisplay = new HashMap<>(); //Visible POIs
	private final ViewGroup container;
	private Vector2d containerSize = new Vector2d(0, 0);

	public AugmentedRealityViewManager(ViewGroup container) {
		this.container = container;
	}

	public Vector2d getContainerSize() {
		return containerSize;
	}

	public void postInit() {
		containerSize = new Vector2d(container.getMeasuredWidth(), container.getMeasuredHeight());

		RelativeLayout.LayoutParams params =
				(RelativeLayout.LayoutParams) container.getLayoutParams();
		double width = Math.sqrt((containerSize.x * containerSize.x)
				+ (containerSize.y * containerSize.y));
		int offset =
				(int) Math.ceil(Math.abs(width - Math.min(containerSize.x, containerSize.y)) / 2);
		params.setMargins(-1 * offset, -1 * offset, -1 * offset, -1 * offset);
		params.width = (int) Math.ceil(width);

		container.setLayoutParams(params);
		containerSize.x += 2 * offset;
		containerSize.y += 2 * offset;
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
		newViewElement.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				NodeDialogBuilder.showNodeInfoDialog(parent, poi);
			}
		});
		PoiMarkerDrawable icon = new PoiMarkerDrawable(parent, new DisplayableGeoNode(poi),
				DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);

		((ImageButton) newViewElement).setImageDrawable(icon.getDrawable());

		return newViewElement;
	}

	private void updateViewElement(View pButton, GeoNode poi, float stackOrder) {
		double size = calculateSizeInPixels(poi.distanceMeters);
		Vector2d objSize =
				new Vector2d(size * MarkerUtils.IconType.poiRouteIcon.getAspectRatio(), size);

		Vector4d pos = AugmentedRealityUtils.getXYPosition(poi.difDegAngle,
				-Globals.virtualCamera.degPitch,
				0, Globals.virtualCamera.screenRotation, objSize,
				Globals.virtualCamera.angleOfViewDeg, getContainerSize());

		float xPos = (float) pos.x;
		float yPos = (float) pos.y;
		float roll = (float) pos.w;

		// A layout pass is only needed when the icon actually changes pixel size, which follows
		// the (slow) distance updates rather than the orientation ones.
		ViewGroup.LayoutParams params = pButton.getLayoutParams();
		int width = (int) objSize.x;
		int height = (int) objSize.y;
		if (params.width != width || params.height != height) {
			params.width = width;
			params.height = height;
			pButton.setLayoutParams(params);
		}

		pButton.setX(xPos);
		pButton.setY(yPos);
		pButton.setRotation(roll);

		// Z decides both draw order and touch dispatch order, so the caller's stacking is kept
		// without reordering the container's children on every frame.
		pButton.setZ(stackOrder);
	}

	private double calculateSizeInPixels(double distance) {
		double scale;
		if (distance > UIConstants.UI_CLOSE_TO_FAR_THRESHOLD_METERS) {
			scale = AugmentedRealityUtils.remapScale(
					UIConstants.UI_CLOSE_TO_FAR_THRESHOLD_METERS,
					(int) Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue,
					UIConstants.UI_FAR_MAX_SCALE_DP,
					UIConstants.UI_FAR_MIN_SCALE_DP, distance);
		} else {
			scale = AugmentedRealityUtils.remapScale(
					(int) Configs.ConfigKey.maxNodesShowDistanceLimit.minValue,
					UIConstants.UI_CLOSE_TO_FAR_THRESHOLD_METERS,
					UIConstants.UI_CLOSEUP_MAX_SCALE_DP,
					UIConstants.UI_CLOSEUP_MIN_SCALE_DP, distance);
		}

		return Globals.convertDpToPixel((float) scale);
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
