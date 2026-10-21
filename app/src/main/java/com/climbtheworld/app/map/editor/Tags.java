package com.climbtheworld.app.map.editor;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.views.StyleSwitches;

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
		StyleSwitches.add(parent, tagsView.findViewById(R.id.containerClimbingStyles),
				poi.getClimbingStyles(), (compoundButton, b) -> {
					saveStyles(poi);
					refreshEditor();
				});
	}

	void saveStyles(GeoNode poi) {
		poi.setClimbingStyles(
				StyleSwitches.checked(tagsView.findViewById(R.id.containerClimbingStyles)));
	}

	/** Tells the editor a change shows on its map. */
	protected void refreshEditor() {
		if (parent instanceof ITagsEvents) {
			((ITagsEvents) parent).doRefresh();
		}
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
