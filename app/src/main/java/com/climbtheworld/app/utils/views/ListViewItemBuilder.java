package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.UIConstants;

public class ListViewItemBuilder {
	private View view;

	private ImageView imageView;
	private SwitchCompat checkBox;
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

	public ListViewItemBuilder setIconSize(Double width, Double height) {
		this.imageView.getLayoutParams().height = height.intValue();
		this.imageView.getLayoutParams().width = width.intValue();

		return this;
	}

	/**
	 * Lays the row out as a row of the display filters, flush with their other entries, the
	 * grade labels: no padding on the sides, and the icon in a POI_TYPE_LIST_ICON_SIZE column,
	 * drawn against its start edge at its own size rather than centred in it. The icons, of
	 * different widths (a pin, a style block, the bookmark star), then all start where the grade
	 * labels do, and the names all after the same column.
	 */
	public ListViewItemBuilder setFilterRowIcon(Drawable icon) {
		setIcon(icon);
		setIconSize(UIConstants.POI_TYPE_LIST_ICON_SIZE, UIConstants.POI_TYPE_LIST_ICON_SIZE);
		alignIconStart();
		return withoutSidePadding();
	}

	/**
	 * No padding on the sides, for a row among entries laid out in a layout file, flush with the
	 * edges of their section (ItemSubSectionContainer): the settings' and display filters' rows,
	 * in line with the labels, seek bars and spinners around them.
	 */
	public ListViewItemBuilder withoutSidePadding() {
		view.setPaddingRelative(0, view.getPaddingTop(), 0, view.getPaddingBottom());
		return this;
	}

	/**
	 * Draws the icon against the start edge of its view, centred on its height, scaled down to
	 * fit if bigger: in a drawable the size of the view's content, the icon placed in it.
	 */
	private void alignIconStart() {
		Drawable icon = imageView.getDrawable();
		// The view's content, without the gap to the name on its end side.
		int boxWidth = imageView.getLayoutParams().width
				- imageView.getPaddingStart() - imageView.getPaddingEnd();
		int boxHeight = imageView.getLayoutParams().height
				- imageView.getPaddingTop() - imageView.getPaddingBottom();
		if (icon == null || boxWidth <= 0 || boxHeight <= 0
				|| icon.getIntrinsicWidth() <= 0 || icon.getIntrinsicHeight() <= 0) {
			return;
		}

		float scale = Math.min(1f, Math.min((float) boxWidth / icon.getIntrinsicWidth(),
				(float) boxHeight / icon.getIntrinsicHeight()));
		int width = Math.round(icon.getIntrinsicWidth() * scale);
		int height = Math.round(icon.getIntrinsicHeight() * scale);

		LayerDrawable box = new LayerDrawable(new Drawable[]{icon});
		box.setLayerSize(0, width, height);
		box.setLayerGravity(0, Gravity.START | Gravity.CENTER_VERTICAL);
		// The insets make up the rest of the box, on the end side (the left one right to left).
		box.setLayerInsetEnd(0, boxWidth - width);
		box.setLayerInsetTop(0, (boxHeight - height) / 2);
		box.setLayerInsetBottom(0, boxHeight - height - (boxHeight - height) / 2);
		imageView.setImageDrawable(box);
	}
}
