package com.climbtheworld.app.utils;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.climbtheworld.app.R;

public class ViewUtils {

    public static void addSwitch(ViewGroup viewContainer, CompoundButton.OnCheckedChangeListener listener, Configs.ConfigKey config) {
        Context parent = viewContainer.getContext();
        View newView = ListViewItemBuilder.getBuilder(viewContainer.getContext())
                .setTitle(parent.getString(config.stringId))
                .setDescription(parent.getString(config.descriptionId))
                .setSwitchChecked(Configs.instance(parent).getBoolean(config))
                .setSwitchEvent(listener)
                .addVerticalPadding()
                .changeElementId(R.id.switchTypeEnabled, config.stringId)
                .build();
        viewContainer.addView(newView);
    }
}
