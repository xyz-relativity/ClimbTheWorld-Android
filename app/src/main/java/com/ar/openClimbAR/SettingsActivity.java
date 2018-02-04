package com.ar.openClimbAR;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;

import com.ar.openClimbAR.tools.GradeConverter;
import com.ar.openClimbAR.utils.Globals;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ((SeekBar)findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getMaxVisibleNodesCountLimit()/10);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getMaxVisibleNodesDistanceLimit()/10);
        ((SeekBar)findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getMaxVisibleNodesDistanceLimit()/10);

        Spinner dropdown = findViewById(R.id.gradeSpinner);
        List<String> allGrades = GradeConverter.getConverter().systems;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, allGrades);
        dropdown.setAdapter(adapter);
        dropdown.setSelection(allGrades.indexOf(Globals.globalConfigs.getDisplaySystem()));

        ((Switch)findViewById(R.id.screenSwitch)).setChecked(Globals.globalConfigs.getKeepScreenOn());
        ((Switch)findViewById(R.id.mapMobileDataSwitch)).setChecked(Globals.globalConfigs.getUseMobileDataForMap());
        ((Switch)findViewById(R.id.poiMobileDataSwitch)).setChecked(Globals.globalConfigs.getUseMobileDataForRoutes());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        findViewById(R.id.gradeSystemLayout).invalidate();
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }
}
