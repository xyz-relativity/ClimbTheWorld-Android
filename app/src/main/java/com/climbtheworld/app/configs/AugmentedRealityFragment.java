package com.climbtheworld.app.configs;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.SettingsActivity;

import androidx.appcompat.app.AppCompatActivity;

public class AugmentedRealityFragment extends ConfigFragment implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
    private final Configs configs;
    private int countMultiplier;
    private int distanceMultiplier;

    public AugmentedRealityFragment(AppCompatActivity parent, View view) {
        super(parent, view);

        configs = Configs.instance(parent);

        countMultiplier = ((int) Configs.ConfigKey.maxNodesShowCountLimit.maxValue) / 10;
        distanceMultiplier = ((int)Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue) / 10;

        uiSetup();
    }

    private void uiSetup() {
        //route display filters
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setMax((int) Configs.ConfigKey.maxNodesShowCountLimit.maxValue / countMultiplier);
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setProgress(configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit) / countMultiplier);
        ((SeekBar) findViewById(R.id.maxViewCountSeek)).setOnSeekBarChangeListener(this);
        ((TextView) findViewById(R.id.maxViewCountValue)).setText(String.valueOf(configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit)));

        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setMax((int) Configs.ConfigKey.maxNodesShowDistanceLimit.maxValue / distanceMultiplier);
        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setProgress(configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit) / distanceMultiplier);
        ((SeekBar) findViewById(R.id.maxViewDistanceSeek)).setOnSeekBarChangeListener(this);
        ((TextView) findViewById(R.id.maxViewDistanceValue)).setText(String.valueOf(configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit)));

        SettingsActivity.addSwitch((ViewGroup)findViewById(R.id.linerLayoutRouteSettings), this, Configs.ConfigKey.showVirtualHorizon);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (seekBar.getId() == R.id.maxViewCountSeek) {
                configs.setInt(Configs.ConfigKey.maxNodesShowCountLimit, progress * countMultiplier);
                ((TextView)findViewById(R.id.maxViewCountValue)).setText(String.valueOf(progress * countMultiplier));
            }

            if (seekBar.getId() == R.id.maxViewDistanceSeek) {
                configs.setInt(Configs.ConfigKey.maxNodesShowDistanceLimit, progress * distanceMultiplier);
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

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (compoundButton.getId() == Configs.ConfigKey.showVirtualHorizon.stringId) {
            configs.setBoolean(Configs.ConfigKey.showVirtualHorizon, isChecked);
        }
    }
}
