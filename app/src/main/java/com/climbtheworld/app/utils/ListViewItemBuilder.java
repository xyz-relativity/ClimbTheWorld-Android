package com.climbtheworld.app.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;

public class ListViewItemBuilder {
	private View view;

	private ImageView imageView;
	private Switch checkBox;
	private TextView titleView;
	private TextView descriptionView;

	public static final int DEFAULT_VERTICAL_PADDING = Globals.convertDpToPixel(5).intValue();
	public static final int DEFAULT_HORIZONTAL_PADDING = Globals.convertDpToPixel(2).intValue();

	public static ListViewItemBuilder getPaddedBuilder(Context parent) {
		return getPaddedBuilder(parent, null, false);
	}

	public static ListViewItemBuilder getPaddedBuilder(Context parent, View view, boolean clickable) {
		return new ListViewItemBuilder(parent, view, clickable, true);
	}

	public static ListViewItemBuilder getNonPaddedBuilder(Context parent) {
		return getNonPaddedBuilder(parent, null, false);
	}

	public static ListViewItemBuilder getNonPaddedBuilder(Context parent, View view, boolean clickable) {
		return new ListViewItemBuilder(parent, view, clickable, false);
	}

	private ListViewItemBuilder(Context parent, View parentView, boolean clickable, boolean withVerticalPadding) {
		if (parentView == null) {
			if (clickable) {
				this.view = View.inflate(parent, R.layout.list_item_switch_description_clickable, null);
			} else {
				this.view = View.inflate(parent, R.layout.list_item_switch_description, null);
			}
		} else {
			this.view = parentView;
		}

		if (withVerticalPadding) {
			view.setPadding(DEFAULT_HORIZONTAL_PADDING, DEFAULT_VERTICAL_PADDING, DEFAULT_HORIZONTAL_PADDING, DEFAULT_VERTICAL_PADDING);
		}

		imageView = view.findViewById(R.id.imageIcon);
		imageView.setVisibility(View.GONE);
		checkBox = view.findViewById(R.id.switchTypeEnabled);
		checkBox.setVisibility(View.GONE);
		titleView = view.findViewById(R.id.textTypeName);
		titleView.setVisibility(View.GONE);
		descriptionView = view.findViewById(R.id.textTypeDescription);
		descriptionView.setVisibility(View.GONE);
	}

	public ListViewItemBuilder setIcon(final Drawable icon) {
		Drawable setIcon = icon;
		if (icon instanceof PoiMarkerDrawable) {
			setIcon = ((PoiMarkerDrawable) icon).getDrawable();
		}
		this.imageView.setImageDrawable(setIcon);
		this.imageView.setVisibility(View.VISIBLE);
		return this;
	}

	public ListViewItemBuilder setSwitchChecked(boolean checked) {
		this.checkBox.setChecked(checked);
		this.checkBox.setVisibility(View.VISIBLE);
		return this;
	}

	public ListViewItemBuilder setSwitchEvent(CompoundButton.OnCheckedChangeListener listener) {
		if (listener != null) {
			this.checkBox.setOnCheckedChangeListener(listener);
			this.checkBox.setVisibility(View.VISIBLE);
		}
		return this;
	}

	public ListViewItemBuilder setTitle(String title) {
		this.titleView.setText(title);
		this.titleView.setVisibility(View.VISIBLE);

		return this;
	}

	public ListViewItemBuilder setDescription(String description) {
		this.descriptionView.setText(description);
		this.descriptionView.setVisibility(View.VISIBLE);

		return this;
	}

	public ListViewItemBuilder changeElementId(int element, int id) {
		this.view.findViewById(element).setId(id);

		return this;
	}

	public View build() {
		return this.view;
	}
}
