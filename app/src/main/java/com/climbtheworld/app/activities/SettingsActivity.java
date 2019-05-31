package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.SpinnerUtils;
import com.climbtheworld.app.utils.ViewUtils;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SettingsActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {

    private int countMultiplier;
    private int distanceMultiplier;
    private Spinner minSpinner;
    private Spinner maxSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        countMultiplier = ((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue) / 10;
        distanceMultiplier = ((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue) / 10;

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
        ((EditText)findViewById(R.id.editCallsign)).setFilters(new InputFilter[] { filter });

        uiSetup();
    }

    private void uiSetup() {
        //Device settings
        ((EditText)findViewById(R.id.editCallsign)).setText(Globals.globalConfigs.getString(Configs.ConfigKey.callsign));
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.keepScreenOn);
//        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useArCore);
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForMap);
        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutDeviceSettings), this, Configs.ConfigKey.useMobileDataForRoutes);

        //route settings
        Spinner dropdown = findViewById(R.id.gradeSelectSpinner);
        dropdown.setOnItemSelectedListener(null);

        dropdown.setAdapter(new GradeSystem.GradeSystemArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));

        dropdown.setSelection(GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
        dropdown.setOnItemSelectedListener(this);

        ViewUtils.addSwitch((ViewGroup)findViewById(R.id.linerLayoutRouteSettings), this, Configs.ConfigKey.showVirtualHorizon);

        //route display filters
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setMax((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit) / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)));

        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setMax((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit) / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit)));

        minSpinner = findViewById(R.id.gradeFilterSpinnerMin);
        maxSpinner = findViewById(R.id.gradeFilterSpinnerMax);

        SpinnerUtils.updateLinkedGradeSpinners(this,
                minSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade),
                maxSpinner,
                Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade),
                true, false);

        maxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        minSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMinGrade, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        loadStyles();
        loadNodeTypes();
    }

    private void loadStyles() {
        Map<String, GeoNode.ClimbingStyle> climbStyle = new TreeMap<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.ClimbingStyle> checked = Globals.globalConfigs.getClimbingStyles();

        RadioGroup container = findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(this, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            styleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    saveStyles();
                }
            });

            container.addView(customSwitch);
        }
    }

    private void saveStyles() {
        Set<GeoNode.ClimbingStyle> styles = new TreeSet<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            Switch styleCheckBox = findViewById(style.getNameId());
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }

        Globals.globalConfigs.setClimbingStyles(styles);
    }

    private void loadNodeTypes() {
        Map<String, GeoNode.NodeTypes> climbStyle = new TreeMap<>();
        for (GeoNode.NodeTypes style: GeoNode.NodeTypes.values())
        {
            climbStyle.put(style.name(), style);
        }

        Set<GeoNode.NodeTypes> checked = Globals.globalConfigs.getNodeTypes();

        RadioGroup container = findViewById(R.id.radioGroupTypes);

        for (GeoNode.NodeTypes styleName: climbStyle.values())
        {
            View customSwitch = ViewUtils.buildCustomSwitch(this, styleName.getNameId(), styleName.getDescriptionId(), checked.contains(styleName), null);
            Switch styleCheckBox = customSwitch.findViewById(R.id.switchTypeEnabled);
            styleCheckBox.setId(styleName.getNameId());

            styleCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    saveTypes();
                }
            });

            container.addView(customSwitch);
        }
    }

    private void saveTypes() {
        Set<GeoNode.NodeTypes> styles = new TreeSet<>();
        for (GeoNode.NodeTypes style: GeoNode.NodeTypes.values())
        {
            Switch styleCheckBox = findViewById(style.getNameId());
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }

        Globals.globalConfigs.setNodeTypes(styles);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Globals.onResume(this);
    }

    @Override
    protected void onPause() {
        Globals.onPause(this);

        String callsign = ((EditText)findViewById(R.id.editCallsign)).getText().toString();
        if (callsign.isEmpty()) {
            callsign = "Unnamed";
        }

        Globals.globalConfigs.setString(Configs.ConfigKey.callsign, callsign);

        super.onPause();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (seekBar.getId() == R.id.maxViewCountSeek) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.maxNodesShowCountLimit, progress * countMultiplier);
                ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(progress * countMultiplier));
            }

            if (seekBar.getId() == R.id.maxViewDistanceSeek) {
                Globals.globalConfigs.setInt(Configs.ConfigKey.maxNodesShowDistanceLimit, progress * distanceMultiplier);
                ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(progress * distanceMultiplier));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.gradeSelectSpinner:
                Globals.globalConfigs.setString(Configs.ConfigKey.usedGradeSystem, GradeSystem.printableValues()[position].getMainKey());
                SpinnerUtils.updateLinkedGradeSpinners(this, minSpinner, Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade), maxSpinner, Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade), true, false);
                break;
            case R.id.gradeFilterSpinnerMax:
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, position);
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

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
