package com.ar.openClimbAR;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.utils.Configs;
import com.ar.openClimbAR.utils.Globals;

import java.util.List;

public class SettingsActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemSelectedListener,
        CompoundButton.OnCheckedChangeListener {

    private static final int COUNT_MULTIPLIER = 10;
    private static final int DISTANCE_MULTIPLIER = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setMax((int)Configs.ConfigKey.maxNodesShowCountLimit.maxValue / COUNT_MULTIPLIER);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getMaxVisibleNodesCountLimit() / COUNT_MULTIPLIER);
        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(Globals.globalConfigs.getMaxVisibleNodesCountLimit()));

        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setMax((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / DISTANCE_MULTIPLIER);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getMaxVisibleNodesDistanceLimit() / DISTANCE_MULTIPLIER);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(Globals.globalConfigs.getMaxVisibleNodesDistanceLimit()));

        Spinner dropdown = findViewById(R.id.gradeSpinner);
        List<String> allGrades = GradeConverter.getConverter().cleanSystems;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(allGrades.indexOf(Globals.globalConfigs.getDisplaySystem()));
        dropdown.setOnItemSelectedListener(this);

        ((Switch)findViewById(R.id.screenSwitch)).setChecked(Globals.globalConfigs.getKeepScreenOn());
        ((Switch)findViewById(R.id.screenSwitch)).setOnCheckedChangeListener(this);

        ((Switch)findViewById(R.id.mapMobileDataSwitch)).setChecked(Globals.globalConfigs.getUseMobileDataForMap());
        ((Switch)findViewById(R.id.mapMobileDataSwitch)).setOnCheckedChangeListener(this);

        ((Switch)findViewById(R.id.poiMobileDataSwitch)).setChecked(Globals.globalConfigs.getUseMobileDataForRoutes());
        ((Switch)findViewById(R.id.poiMobileDataSwitch)).setOnCheckedChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (seekBar.getId() == R.id.maxViewCountSeek) {
                Globals.globalConfigs.setMaxVisibleNodesCountLimit(progress * COUNT_MULTIPLIER);
                ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(progress * COUNT_MULTIPLIER));
            }

            if (seekBar.getId() == R.id.maxViewDistanceSeek) {
                Globals.globalConfigs.setMaxVisibleNodesDistanceLimit(progress * DISTANCE_MULTIPLIER);
                ((TextView)findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(progress * DISTANCE_MULTIPLIER));
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
        Globals.globalConfigs.setDisplaySystem(GradeConverter.getConverter().cleanSystems.get(position));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.screenSwitch) {
            Globals.globalConfigs.setKeepScreenOn(isChecked);
        }

        if (buttonView.getId() == R.id.mapMobileDataSwitch) {
            Globals.globalConfigs.setUseMobileDataForMap(isChecked);
        }

        if (buttonView.getId() == R.id.poiMobileDataSwitch) {
            Globals.globalConfigs.setUseMobileDataForRoutes(isChecked);
        }
    }
}
