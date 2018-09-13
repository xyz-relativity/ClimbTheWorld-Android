package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

public class DataUsageFragment extends TutorialFragment implements CompoundButton.OnCheckedChangeListener {

    public DataUsageFragment(Activity parent, int viewID) {
        super(parent, viewID);
    }

    @Override
    public void onCreate(ViewGroup view) {
        ((TextView)parent.findViewById(R.id.fragmentText))
                .setText(Html.fromHtml(parent.getResources().getString(R.string.tutorial_data_usage_message)));
        ((TextView)parent.findViewById(R.id.fragmentText)).setMovementMethod(LinkMovementMethod.getInstance());

        ((Switch)parent.findViewById(R.id.mapMobileDataSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForMap));
        ((Switch)parent.findViewById(R.id.mapMobileDataSwitch)).setOnCheckedChangeListener(this);

        ((Switch)parent.findViewById(R.id.poiMobileDataSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForRoutes));
        ((Switch)parent.findViewById(R.id.poiMobileDataSwitch)).setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.mapMobileDataSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForMap, isChecked);
        }

        if (buttonView.getId() == R.id.poiMobileDataSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForRoutes, isChecked);
        }
    }
}
