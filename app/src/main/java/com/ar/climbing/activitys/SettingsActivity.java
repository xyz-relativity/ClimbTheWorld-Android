package com.ar.climbing.activitys;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.tools.GradeConverter;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Globals;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SettingsActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {

    private int countMultiplier;
    private int distanceMultiplier;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        countMultiplier = ((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue) / 10;
        distanceMultiplier = ((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue) / 10;

        uiSetup();
    }

    private void uiSetup() {
        //Device settings
        ((Switch)findViewById(R.id.screenSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn));
        ((Switch)findViewById(R.id.screenSwitch)).setOnCheckedChangeListener(this);

        ((Switch)findViewById(R.id.mapMobileDataSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForMap));
        ((Switch)findViewById(R.id.mapMobileDataSwitch)).setOnCheckedChangeListener(this);

        ((Switch)findViewById(R.id.poiMobileDataSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.useMobileDataForRoutes));
        ((Switch)findViewById(R.id.poiMobileDataSwitch)).setOnCheckedChangeListener(this);

        //route settings
        Spinner dropdown = findViewById(R.id.gradeSpinner);
        dropdown.setOnItemSelectedListener(null);
        List<String> allGrades = GradeConverter.getConverter().cleanSystems;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(allGrades.indexOf(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)), false);
        dropdown.setOnItemSelectedListener(this);

        ((Switch)findViewById(R.id.virtualHorizonSwitch)).setChecked(Globals.globalConfigs.getBoolean(Configs.ConfigKey.showVirtualHorizon));
        ((Switch)findViewById(R.id.virtualHorizonSwitch)).setOnCheckedChangeListener(this);

        //route display filters
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setMax((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit) / countMultiplier);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)));

        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setMax((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit) / distanceMultiplier);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit)));

        ((TextView)findViewById(R.id.filterMinGrade)).setText(getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        updateMinSpinner();

        ((TextView)findViewById(R.id.filterMaxGrade)).setText(getResources().getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        updateMaxSpinner();

        for (GeoNode.ClimbingStyle style: Globals.globalConfigs.getClimbingStyles())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null) {
                styleCheckBox.setChecked(true);
            }
        }
    }

    private void updateMinSpinner() {
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMin)).setOnItemSelectedListener(null);
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Disable click item < month current
            @Override
            public boolean isEnabled(int position) {
                return (Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade) == 0)
                        || (position <= Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade));
            }

            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup parent) {
                View mView = super.getDropDownView(position, convertView, parent);
                TextView mTextView = (TextView) mView;
                if (isEnabled(position)) {
                    mTextView.setTextColor(Color.BLACK);
                } else {
                    mTextView.setTextColor(Color.GRAY);
                }
                return mView;
            }
        };
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMin)).setAdapter(adapter);
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMin)).setSelection(Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade), false);
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMin)).setOnItemSelectedListener(this);
    }

    private void updateMaxSpinner() {
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMax)).setOnItemSelectedListener(null);
        List<String> allGrades = GradeConverter.getConverter().getAllGrades(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, allGrades) {
            // Disable click item < month current
            @Override
            public boolean isEnabled(int position) {
                return (Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade) == 0
                        || position >= Globals.globalConfigs.getInt(Configs.ConfigKey.filterMinGrade));
            }

            // Change color item
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup parent) {
                View mView = super.getDropDownView(position, convertView, parent);
                TextView mTextView = (TextView) mView;
                if (isEnabled(position)) {
                    mTextView.setTextColor(Color.BLACK);
                } else {
                    mTextView.setTextColor(Color.GRAY);
                }
                return mView;
            }
        };
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMax)).setAdapter(adapter);
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMax)).setSelection(Globals.globalConfigs.getInt(Configs.ConfigKey.filterMaxGrade), false);
        ((Spinner) findViewById(R.id.gradeFilterSpinnerMax)).setOnItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Set<GeoNode.ClimbingStyle> styles = new TreeSet<>();
        for (GeoNode.ClimbingStyle style: GeoNode.ClimbingStyle.values())
        {
            int id = getResources().getIdentifier(style.name(), "id", getPackageName());
            CheckBox styleCheckBox = findViewById(id);
            if (styleCheckBox != null && styleCheckBox.isChecked()) {
                styles.add(style);
            }
        }

        Globals.globalConfigs.setClimbingStyles(styles);

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
            case R.id.gradeSpinner:
                Globals.globalConfigs.setString(Configs.ConfigKey.usedGradeSystem, GradeConverter.getConverter().cleanSystems.get(position));
                break;
            case R.id.gradeFilterSpinnerMin:
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMinGrade, position);
                break;
            case R.id.gradeFilterSpinnerMax:
                Globals.globalConfigs.setInt(Configs.ConfigKey.filterMaxGrade, position);
                break;
        }
        uiSetup();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.virtualHorizonSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.showVirtualHorizon, isChecked);
        }

        if (buttonView.getId() == R.id.screenSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.keepScreenOn, isChecked);
        }

        if (buttonView.getId() == R.id.mapMobileDataSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForMap, isChecked);
        }

        if (buttonView.getId() == R.id.poiMobileDataSwitch) {
            Globals.globalConfigs.setBoolean(Configs.ConfigKey.useMobileDataForRoutes, isChecked);
        }
    }
}
