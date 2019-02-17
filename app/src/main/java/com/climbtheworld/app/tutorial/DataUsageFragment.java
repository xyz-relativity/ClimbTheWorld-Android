package com.climbtheworld.app.tutorial;

import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ViewUtils;

public class DataUsageFragment extends TutorialFragment implements CompoundButton.OnCheckedChangeListener {

    public DataUsageFragment(AppCompatActivity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)view.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_data_usage_message)));
        ((TextView)view.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        ViewUtils.addSwitch((ViewGroup)view.findViewById(R.id.linearLayoutSwitches), this, Configs.ConfigKey.useMobileDataForMap);
        ViewUtils.addSwitch((ViewGroup)view.findViewById(R.id.linearLayoutSwitches), this, Configs.ConfigKey.useMobileDataForRoutes);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == Configs.ConfigKey.useMobileDataForMap.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForMap, isChecked);
        }

        if (buttonView.getId() == Configs.ConfigKey.useMobileDataForRoutes.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForRoutes, isChecked);
        }
    }
}
