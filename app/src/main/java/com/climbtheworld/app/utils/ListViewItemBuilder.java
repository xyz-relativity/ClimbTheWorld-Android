package com.climbtheworld.app.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.marker.PoiMarkerDrawable;

public class ListViewItemBuilder {
    private final Context parent;
    private View view;

    private ImageView imageView;
    private Switch checkBox;
    private Button actionButton;
    private TextView titleView;
    private TextView descriptionView;

    public static final int DEFAULT_PADDING = Math.round(Globals.convertDpToPixel(5));

    public static ListViewItemBuilder getBuilder(Context parent)
    {
        return getBuilder(parent, null, false);
    }

    public static ListViewItemBuilder getBuilder(Context parent, View view, boolean clickable)
    {
        return new ListViewItemBuilder(parent, view, clickable);
    }

    private ListViewItemBuilder(Context parent, View parentView, boolean clickable) {
        this.parent = parent;
        if (parentView == null) {
            if (clickable) {
                this.view = View.inflate(parent, R.layout.list_item_switch_description_clickable, null);
            } else {
                this.view = View.inflate(parent, R.layout.list_item_switch_description, null);
            }
        } else {
            this.view = parentView;
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
        this.checkBox.setOnCheckedChangeListener(listener);
        this.checkBox.setVisibility(View.VISIBLE);
        return this;
    }

    public ListViewItemBuilder setPadding(int left, int top, int right, int bottom) {
        view.setPadding(left, top, right, bottom);
        return this;
    }

    public ListViewItemBuilder setButtonText(String text) {
        this.actionButton.setText(text);
        this.actionButton.setVisibility(View.VISIBLE);

        return this;
    }

    public ListViewItemBuilder setTitle(String title) {
        this.titleView.setText(title);
        this.titleView.setVisibility(View.VISIBLE);

        return this;
    }

    public ListViewItemBuilder setDescription (String description) {
        this.descriptionView.setText(description);
        this.descriptionView.setVisibility(View.VISIBLE);

        return this;
    }

    public ListViewItemBuilder changeElementId (int element, int id) {
        this.view.findViewById(element).setId(id);

        return this;
    }

    public View build() {
        return this.view;
    }
}
