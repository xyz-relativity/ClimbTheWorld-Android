package com.climbtheworld.app.utils.views;

import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.StyleGrid;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.constants.UIConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A switch per climbing style, each with its icon, in the order the styles are listed in, see
 * StyleGrid.ORDER.
 */
public final class StyleSwitches {
	private StyleSwitches() {
	}

	/**
	 * Adds the switches to the container, the checked styles on.
	 *
	 * @param filterRows whether they are rows of the display filters, laid out as their other
	 *                   rows: the name only, and the style block in the same icon column as the
	 *                   pins of the type rows, UIConstants.POI_TYPE_LIST_ICON_SIZE. Otherwise,
	 *                   as in the editor, each style's description shows under its name.
	 * @param onChange   told of every switch flipped
	 */
	public static void add(AppCompatActivity parent, ViewGroup container,
	                       Collection<GeoNode.ClimbingStyle> checked, boolean filterRows,
	                       CompoundButton.OnCheckedChangeListener onChange) {
		for (GeoNode.ClimbingStyle style : StyleGrid.all()) {
			List<GeoNode.ClimbingStyle> styles = Collections.singletonList(style);
			ListViewItemBuilder row = ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(style.getNameId()))
					.setSwitchChecked(checked.contains(style))
					.changeElementId(R.id.switchTypeEnabled, style.getNameId())
					.setSwitchEvent(onChange);
			if (filterRows) {
				row.setIcon(MarkerUtils.getStyleIcon(parent, styles,
								UIConstants.FILTER_STYLE_ICON_SIZE))
						.setIconSize(UIConstants.POI_TYPE_LIST_ICON_SIZE,
								UIConstants.POI_TYPE_LIST_ICON_SIZE);
			} else {
				row.setIcon(MarkerUtils.getStyleIcon(parent, styles))
						.setDescription(parent.getString(style.getDescriptionId()));
			}
			container.addView(row.build());
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
