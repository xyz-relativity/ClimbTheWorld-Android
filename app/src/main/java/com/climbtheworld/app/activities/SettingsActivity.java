package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.filter.FilterFragment;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ViewUtils;

public class SettingsActivity extends AppCompatActivity
        implements CompoundButton.OnCheckedChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        InputFilter filter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!Character.isLetterOrDigit(source.charAt(i))) {
                        return "";
                    }
                }
                return null;
            }
        };

        uiSetup();
    }

    private void uiSetup() {
        //Device settings
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.keepScreenOn);
//        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useArCore);
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForMap);
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForRoutes);

        FilterFragment filter = new FilterFragment(this, findViewById(android.R.id.content));

        //route settings
        Spinner dropdown = findViewById(R.id.gradeSelectSpinner);
        dropdown.setOnItemSelectedListener(null);

        dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

        dropdown.setSelection(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
        dropdown.setOnItemSelectedListener(filter);

        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutRouteSettings), this, Configs.ConfigKey.showVirtualHorizon);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Globals.onResume(this);
    }

    @Override
    protected void onPause() {
        Globals.onPause(this);
        super.onPause();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == Configs.ConfigKey.showVirtualHorizon.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.showVirtualHorizon, isChecked);
        }

        if (buttonView.getId() == Configs.ConfigKey.useArCore.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useArCore, isChecked);
        }

        if (buttonView.getId() == Configs.ConfigKey.keepScreenOn.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.keepScreenOn, isChecked);
        }

        if (buttonView.getId() == Configs.ConfigKey.useMobileDataForMap.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForMap, isChecked);
        }

        if (buttonView.getId() == Configs.ConfigKey.useMobileDataForRoutes.stringId) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForRoutes, isChecked);
        }
    }
}
