package com.climbtheworld.app.configs;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.TextViewSwitcher;
import com.climbtheworld.app.walkietalkie.Callsigns;

public class IntercomFragment extends ConfigFragment implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {
	private final Configs configs;
	private TextViewSwitcher callsignSwitcher;

	public IntercomFragment(AppCompatActivity parent, View view) {
		super(parent, view);

		configs = Configs.instance(parent);

		uiSetup();
	}

	private void uiSetup() {
		//route display filters
		addSwitch(findViewById(R.id.linerLayoutIntercomAudioSettings), this, Configs.ConfigKey.intercomHandsFreeSwitch);
		((SeekBar) findViewById(R.id.noiseFilterSeek)).setMax((int) Configs.ConfigKey.intercomHandsFreeNoiseFilter.maxValue);
		((SeekBar) findViewById(R.id.noiseFilterSeek)).setProgress(configs.getInt(Configs.ConfigKey.intercomHandsFreeNoiseFilter));
		((SeekBar) findViewById(R.id.noiseFilterSeek)).setOnSeekBarChangeListener(this);
		((TextView) findViewById(R.id.noiseFilterValue)).setText(String.valueOf(configs.getInt(Configs.ConfigKey.intercomHandsFreeNoiseFilter)));

		callsignSwitcher = new TextViewSwitcher(parent, findViewById(R.id.callsignLayout), configs.getCallsign(), new TextViewSwitcher.ISwitcherCallback() {
			@Override
			public void onChange(String value) {
				if (Callsigns.isBlank(value)) {
					// A blank callsign is never sent, see Callsigns: the field shows again the
					// name the others still see.
					callsignSwitcher.setText(configs.getCallsign());
					return;
				}
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
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			if (seekBar.getId() == R.id.noiseFilterSeek) {
				configs.setInt(Configs.ConfigKey.intercomHandsFreeNoiseFilter, progress);
				((TextView) findViewById(R.id.noiseFilterValue)).setText(String.valueOf(progress));
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
