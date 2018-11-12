package com.climbtheworld.app.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;

public class ViewUtils {

    public static void addSwitch(ViewGroup viewContainer, CompoundButton.OnCheckedChangeListener listener, Configs.ConfigKey config) {
        View newView = ViewUtils.buildCustomSwitch(viewContainer.getContext(), config.stringId, config.descriptionId, Globals.globalConfigs.getBoolean(config), null);
        Switch inSwitch = newView.findViewById(R.id.switchTypeEnabled);
        inSwitch.setOnCheckedChangeListener(listener);
        inSwitch.setId(config.stringId);
        viewContainer.addView(newView);
    }

    public static View buildCustomSwitch(Context parent, int name, int description, Boolean checked, Drawable image) {
        return buildCustomSwitch(parent, parent.getString(name), parent.getString(description), checked, image);
    }

    public static View buildCustomSwitch(Context parent, String name, String description, Boolean checked, Drawable image) {
        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.list_item_switch_description, null);

        if (image != null) {
            ((ImageView)v.findViewById(R.id.imageIcon)).setImageDrawable(image);
        } else {
            v.findViewById(R.id.imageIcon).setVisibility(View.GONE);
        }

        if (checked != null) {
            ((Switch)v.findViewById(R.id.switchTypeEnabled)).setChecked(checked);
        } else {
            v.findViewById(R.id.switchTypeEnabled).setVisibility(View.GONE);
        }

        v.findViewById(R.id.imageIcon).setVisibility(View.GONE);
        TextView textView = v.findViewById(R.id.textTypeName);
        textView.setText(name);
        textView = v.findViewById(R.id.textTypeDescription);
        textView.setText(description);

        return v;
    }
}
