package com.climbtheworld.app.configs;

import android.view.View;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.TextViewSwitcher;

public class IntercomFragment extends ConfigFragment implements CompoundButton.OnCheckedChangeListener {
	private final Configs configs;

	public IntercomFragment(AppCompatActivity parent, View view) {
		super(parent, view);

		configs = Configs.instance(parent);

		uiSetup();
	}

	private void uiSetup() {
		//route display filters
		addSwitch(findViewById(R.id.linerLayoutIntercomSettings), this, Configs.ConfigKey.intercomHandsFreeSwitch);
		addSwitch(findViewById(R.id.linerLayoutIntercomSettings), this, Configs.ConfigKey.intercomAllowWiFi);
		addSwitch(findViewById(R.id.linerLayoutIntercomSettings), this, Configs.ConfigKey.intercomAllowBluetooth);
		addSwitch(findViewById(R.id.linerLayoutIntercomSettings), this, Configs.ConfigKey.intercomAllowWiFiDirect);

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
}
