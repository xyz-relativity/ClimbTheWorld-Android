package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Tags {
	private ViewGroup container;
	private View tagsView;
	protected AppCompatActivity parent;

	Tags(AppCompatActivity parent, ViewGroup container, @LayoutRes int resource) {
		this.container = container;
		this.parent = parent;
		this.tagsView = parent.getLayoutInflater().inflate(resource, container, false);

		container.addView(tagsView);
		hideTags();
	}

	void loadStyles(GeoNode poi) {
		List<GeoNode.ClimbingStyle> checked = poi.getClimbingStyles();

		ViewGroup stylesContainer = tagsView.findViewById(R.id.containerClimbingStyles);

		for (GeoNode.ClimbingStyle styleName : Sorters.sortStyles(parent, GeoNode.ClimbingStyle.values())) {
			View customSwitch = ListViewItemBuilder.getPaddedBuilder(parent)
					.setTitle(parent.getString(styleName.getNameId()))
					.setDescription(parent.getString(styleName.getDescriptionId()))
					.setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(styleName)))
					.setSwitchChecked(checked.contains(styleName))
					.changeElementId(R.id.switchTypeEnabled, styleName.getNameId())
					.setSwitchEvent(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
							saveStyles(poi);
							((EditNodeActivity) parent).updateMapMarker();
						}
					})
					.build();

			stylesContainer.addView(customSwitch);
		}
	}

	void saveStyles(GeoNode poi) {
		List<GeoNode.ClimbingStyle> styles = new ArrayList<>();
		ViewGroup stylesContainer = tagsView.findViewById(R.id.containerClimbingStyles);
		for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
			SwitchCompat styleCheckBox = stylesContainer.findViewById(style.getNameId());
			if (styleCheckBox != null && styleCheckBox.isChecked()) {
				styles.add(style);
			}
		}
		poi.setClimbingStyles(styles);
	}

	public boolean isVisible() {
		return tagsView.getVisibility() == View.VISIBLE;
	}

	public void showTags() {
		tagsView.setVisibility(View.VISIBLE);
	}

	public void hideTags() {
		tagsView.setVisibility(View.GONE);
	}
}
