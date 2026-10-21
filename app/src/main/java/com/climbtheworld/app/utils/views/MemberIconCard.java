package com.climbtheworld.app.utils.views;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.utils.Globals;

/**
 * The card of a crag's or area's member, from the list_item_climbing_member_icon layout: its
 * pin with its name under it, and its number in front of the name for a numbered route.
 */
public final class MemberIconCard {
	/** How narrow the name may get, so that a short name still makes a card wide enough to tap. */
	private static final int NAME_MIN_WIDTH = Globals.convertDpToPixel(80).intValue();

	private MemberIconCard() {
	}

	/**
	 * Shows the member on the card, which may have shown another.
	 *
	 * @param routeNumber the route's number in its crag, 0 for a member without one
	 * @return what the card says, its number and name
	 */
	public static String show(AppCompatActivity activity, View card, DisplayableGeoNode pin, String name,
	                          int routeNumber) {
		Drawable icon = new PoiMarkerDrawable(activity, pin, PoiMarkerDrawable.PinShape.slim)
				.getDrawable();
		int iconWidth = Math.max(icon.getIntrinsicWidth() * 2, 1);
		ImageView image = card.findViewById(R.id.memberIconImage);
		image.setImageDrawable(icon);
		image.getLayoutParams().width = iconWidth;
		// All as tall as the tallest pin, which the image puts at the bottom, so that the tips and
		// the names line up along the row whatever the pin.
		image.getLayoutParams().height = MarkerUtils.getTallestPinHeight(activity) * 2;
		card.findViewById(R.id.memberIconLabel).getLayoutParams().width =
				Math.max(iconWidth, NAME_MIN_WIDTH);

		TextView nameView = card.findViewById(R.id.memberIconName);
		nameView.setText(name);
		TextView numberView = card.findViewById(R.id.memberIconNumber);
		if (routeNumber > 0) {
			numberView.setText(routeNumber + ".");
			numberView.setVisibility(View.VISIBLE);
			nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
			name = routeNumber + ". " + name;
		} else {
			numberView.setVisibility(View.GONE);
			nameView.setGravity(Gravity.CENTER);
		}
		card.setContentDescription(name);
		return name;
	}
}
