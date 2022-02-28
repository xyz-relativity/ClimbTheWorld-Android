package com.climbtheworld.app.configs;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.TextViewSwitcher;

public class IntercomFragment extends ConfigFragment implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
	private final Configs configs;

	public IntercomFragment(AppCompatActivity parent, View view) {
		super(parent, view);

		configs = Configs.instance(parent);

		uiSetup();
	}

	private void uiSetup() {
		//route display filters
		addSwitch(findViewById(R.id.linerLayoutIntercomAudioSettings), this, Configs.ConfigKey.intercomHandsFreeSwitch);
		((SeekBar) findViewById(R.id.audioLevelThresholdSeek)).setMax((int) Configs.ConfigKey.intercomHandFreeThreshold.maxValue);
		((SeekBar) findViewById(R.id.audioLevelThresholdSeek)).setProgress(configs.getInt(Configs.ConfigKey.intercomHandFreeThreshold));
		((SeekBar) findViewById(R.id.audioLevelThresholdSeek)).setOnSeekBarChangeListener(this);
		((TextView) findViewById(R.id.audioLevelThresholdValue)).setText(String.valueOf(configs.getInt(Configs.ConfigKey.intercomHandFreeThreshold)));

		addSwitch(findViewById(R.id.linerLayoutIntercomNetworkSettings), this, Configs.ConfigKey.intercomAllowWiFi);
		addSwitch(findViewById(R.id.linerLayoutIntercomNetworkSettings), this, Configs.ConfigKey.intercomAllowBluetooth);
//		addSwitch(findViewById(R.id.linerLayoutIntercomNetworkSettings), this, Configs.ConfigKey.intercomAllowWiFiDirect);
//		addSwitch(findViewById(R.id.linerLayoutIntercomNetworkSettings), this, Configs.ConfigKey.intercomAllowWiFiAware);

		new TextViewSwitcher(parent, findViewById(R.id.callsignLayout), configs.getString(Configs.ConfigKey.intercomCallsign), new TextViewSwitcher.ISwitcherCallback() {
			@Override
			public void onChange(String value) {
				configs.setString(Configs.ConfigKey.intercomCallsign, value);
			}
		});

		new TextViewSwitcher(parent, findViewById(R.id.channelLayout), configs.getString(Configs.ConfigKey.intercomChannel), new TextViewSwitcher.ISwitcherCallback() {
			@Override
			public void onChange(String value) {
				configs.setString(Configs.ConfigKey.intercomChannel, value);
			}
		});
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
		if (compoundButton.getId() == Configs.ConfigKey.intercomHandsFreeSwitch.stringId) {
			configs.setBoolean(Configs.ConfigKey.intercomHandsFreeSwitch, isChecked);
		}

		if (compoundButton.getId() == Configs.ConfigKey.intercomAllowWiFi.stringId) {
			configs.setBoolean(Configs.ConfigKey.intercomAllowWiFi, isChecked);
		}

		if (compoundButton.getId() == Configs.ConfigKey.intercomAllowBluetooth.stringId) {
			configs.setBoolean(Configs.ConfigKey.intercomAllowBluetooth, isChecked);
		}

		if (compoundButton.getId() == Configs.ConfigKey.intercomAllowWiFiDirect.stringId) {
			configs.setBoolean(Configs.ConfigKey.intercomAllowWiFiDirect, isChecked);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			if (seekBar.getId() == R.id.audioLevelThresholdSeek) {
				configs.setInt(Configs.ConfigKey.intercomHandFreeThreshold, progress);
				((TextView) findViewById(R.id.audioLevelThresholdValue)).setText(String.valueOf(progress));
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
