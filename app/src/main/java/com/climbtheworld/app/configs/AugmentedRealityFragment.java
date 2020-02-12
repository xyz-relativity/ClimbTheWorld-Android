package com.climbtheworld.app.configs;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

public class AugmentedRealityFragment extends ConfigFragment implements SeekBar.OnSeekBarChangeListener {
    private int countMultiplier;
    private int distanceMultiplier;

    public AugmentedRealityFragment(AppCompatActivity parent, View view) {
        super(parent, view);

        countMultiplier = ((int) Configs.ConfigKey.maxNodesShowCountLimit.maxValue) / 10;
        distanceMultiplier = ((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue) / 10;

        uiSetup();
    }

    private void uiSetup() {
        //route display filters
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setMax((int) Configs.ConfigKey.maxNodesShowCountLimit.maxValue / countMultiplier);
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit) / countMultiplier);
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView) findViewById(R.id.maxViewCountValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)));

        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setMax((int) Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / distanceMultiplier);
        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setProgress(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit) / distanceMultiplier);
        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView) findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(Globals.globalConfigs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit)));
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

            notifyListeners();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
