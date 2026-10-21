package com.climbtheworld.app.utils.views;

import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** A switch per climbing style, each with its icon, in the order the styles are listed in. */
public final class StyleSwitches {
	private StyleSwitches() {
	}

	/**
	 * Adds the switches to the container, the checked styles on.
	 *
	 * @param onChange told of every switch flipped
	 */
	public static void add(AppCompatActivity parent, ViewGroup container,
	                       Collection<GeoNode.ClimbingStyle> checked,
	                       CompoundButton.OnCheckedChangeListener onChange) {
		for (GeoNode.ClimbingStyle style : Sorters.sortStyles(parent, GeoNode.ClimbingStyle.values())) {
			container.addView(ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(style.getNameId()))
					.setDescription(parent.getString(style.getDescriptionId()))
					.setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(style)))
					.setSwitchChecked(checked.contains(style))
					.changeElementId(R.id.switchTypeEnabled, style.getNameId())
					.setSwitchEvent(onChange)
					.build());
		}
	}

	/** The styles whose switches in the container are on. */
	public static List<GeoNode.ClimbingStyle> checked(ViewGroup container) {
		List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
		for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
			SwitchCompat styleSwitch = container.findViewById(style.getNameId());
			if (styleSwitch != null && styleSwitch.isChecked()) {
				styles.add(style);
			}
		}
		return styles;
	}
}
