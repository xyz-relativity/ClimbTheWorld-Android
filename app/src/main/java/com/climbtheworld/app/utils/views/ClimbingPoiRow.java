package com.climbtheworld.app.utils.views;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;

/**
 * The rows of the lists of climbing POIs: the search results, on the map and on their own page,
 * the map's cluster list, the editors' crag and area picker and the A.R. align list. Each shows
 * the pin as the map draws it, smaller, the name with what the POI is part of under it, see
 * ClimbingParent, the straight line distance from the user over the compass point, and the button
 * centering the map on the POI, the distance part as the info dialogs show it, see
 * DialogueUtils.setDistance.
 */
public final class ClimbingPoiRow {
	/** The size of the pins, against the map's. */
	private static final double PIN_SCALE = 0.78;

	private final AppCompatActivity activity;
	private final Runnable closeList;
	// Where the user is, when it is known better than their last known position; null otherwise.
	private final GeoNode user;

	/**
	 * Rows with the distance from the user's last known position.
	 *
	 * @param closeList closes the list before the map shows a POI, see
	 *                  DialogueUtils.setDistance, null for nothing more
	 */
	public ClimbingPoiRow(AppCompatActivity activity, Runnable closeList) {
		this(activity, closeList, null);
	}

	/**
	 * @param userLatitude  where the user is, when it is known better than their last known
	 *                      position
	 * @param userLongitude where the user is
	 */
	public ClimbingPoiRow(AppCompatActivity activity, Runnable closeList, double userLatitude,
	                      double userLongitude) {
		this(activity, closeList, new GeoNode(userLatitude, userLongitude, 0));
	}

	private ClimbingPoiRow(AppCompatActivity activity, Runnable closeList, GeoNode user) {
		this.activity = activity;
		this.closeList = closeList;
		this.user = user;
	}

	/**
	 * @param recycled    the list's row to reuse, or a new one of a layout including
	 *                    list_item_climbing_poi_content, null for a plain new one
	 * @param parentLabel what the POI is part of, see ClimbingParent#getLabel, empty for nothing
	 */
	public View build(View recycled, DisplayableGeoNode pin, String name, String parentLabel) {
		View row = recycled != null ? recycled
				: View.inflate(activity, R.layout.list_item_climbing_poi, null);
		row.setPadding(ListViewItemBuilder.DEFAULT_HORIZONTAL_PADDING,
				ListViewItemBuilder.DEFAULT_VERTICAL_PADDING,
				ListViewItemBuilder.DEFAULT_HORIZONTAL_PADDING,
				ListViewItemBuilder.DEFAULT_VERTICAL_PADDING);

		Drawable icon = new PoiMarkerDrawable(activity, pin).getDrawable();
		ImageView iconView = row.findViewById(R.id.climbingPoiIcon);
		iconView.setImageDrawable(icon);
		iconView.getLayoutParams().width = (int) (icon.getIntrinsicWidth() * PIN_SCALE);
		iconView.getLayoutParams().height = (int) (icon.getIntrinsicHeight() * PIN_SCALE);

		((TextView) row.findViewById(R.id.climbingPoiName)).setText(name);
		DialogueUtils.setDistance(activity, row, user != null ? user : Globals.virtualCamera,
				pin.getGeoNode(), closeList);
		TextView parentView = row.findViewById(R.id.climbingPoiParent);
		parentView.setText(parentLabel);
		parentView.setVisibility(parentLabel.isEmpty() ? View.GONE : View.VISIBLE);
		return row;
	}
}
