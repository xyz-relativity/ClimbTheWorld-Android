package com.climbtheworld.app.activities;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.UiNetworkManager;
import com.climbtheworld.app.intercom.states.HandsfreeState;
import com.climbtheworld.app.intercom.states.IInterconState;
import com.climbtheworld.app.intercom.states.InterconState;
import com.climbtheworld.app.intercom.states.PushToTalkState;

public class IntercomActivity extends AppCompatActivity {
	private IInterconState activeState;
	private UiNetworkManager networkManager;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	SwitchCompat handsFree;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intercom);

		Ask.on(this)
				.id(500) // in case you are invoking multiple time Ask from same activity or fragment
				.forPermissions(Manifest.permission.RECORD_AUDIO)
				.withRationales(getString(R.string.walkie_talkie_permission_rational)) //optional
				.go();

		configs = Configs.instance(this);

		networkManager = new UiNetworkManager(this, configs);

		handsFree = findViewById(R.id.handsFreeSwitch);
		handsFree.setChecked(configs.getBoolean(Configs.ConfigKey.handsFreeSwitch));
		handsFree.setOnClickListener(this::toggleHandsFree);

		findViewById(R.id.wifiStatusLayout).setOnClickListener(this::onWifiClick);
		findViewById(R.id.bluetoothStatusLayout).setOnClickListener(this::onBluetoothClick);

		PowerManager pm = (PowerManager) getSystemService(IntercomActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
				SwitchCompat handsFree = findViewById(R.id.handsFreeSwitch);
				handsFree.toggle();
				toggleHandsFree(null);
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onStart() {
		super.onStart();
		wakeLock.acquire();
		networkManager.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		toggleHandsFree(null);
		networkManager.onResume();
	}

	@Override
	protected void onPause() {
		networkManager.onPause();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}
		networkManager.onDestroy();
		activeState.finish();
		super.onDestroy();
	}

	private void toggleHandsFree(View v) {
		if (activeState != null) {
			activeState.finish();
		}
		configs.setBoolean(Configs.ConfigKey.handsFreeSwitch, handsFree.isChecked());

		if (handsFree.isChecked()) {
			findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
			activeState = new HandsfreeState(this);
		} else {
			findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
			activeState = new PushToTalkState(this);
		}
		((InterconState) activeState).addListener(networkManager);
	}

	public void onWifiClick(View v) {
		//Creating the instance of PopupMenu
		PopupMenu popup = new PopupMenu(IntercomActivity.this, v);
		//Inflating the Popup using xml file
		popup.getMenuInflater().inflate(R.menu.wifi_options, popup.getMenu());

		popup.getMenu().findItem(R.id.hotspotWifiSettings).setEnabled(isWifiDirectSupported());

		//registering popup with OnMenuItemClickListener
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent menuIntent;
				switch (item.getItemId()) {
					case R.id.wifiSettings:
						menuIntent = new Intent();
						menuIntent.setAction(Settings.ACTION_WIFI_SETTINGS);
						startActivity(menuIntent);
						return true;

					case R.id.hotspotWifiSettings:
						menuIntent = new Intent(Intent.ACTION_MAIN, null);
						menuIntent.addCategory(Intent.CATEGORY_LAUNCHER);
						final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.wifi.p2p.WifiP2pSettings");
						menuIntent.setComponent(cn);
						menuIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(menuIntent);
						return true;
				}
				return false;
			}
		});
		popup.show();//showing popup menu
	}

	private boolean isWifiDirectSupported() {
		PackageManager pm = this.getPackageManager();
		FeatureInfo[] features = pm.getSystemAvailableFeatures();
		for (FeatureInfo info : features) {
			if (info != null && info.name != null && info.name.equalsIgnoreCase("android.hardware.wifi.direct")) {
				return true;
			}
		}
		return false;
	}

	public void onBluetoothClick(View v) {
		//Creating the instance of PopupMenu
		PopupMenu popup = new PopupMenu(IntercomActivity.this, v);
		//Inflating the Popup using xml file
		popup.getMenuInflater().inflate(R.menu.bluetooth_options, popup.getMenu());

		//registering popup with OnMenuItemClickListener
		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent menuIntent;
				switch (item.getItemId()) {
					case R.id.bluetoothSettings:
						menuIntent = new Intent();
						menuIntent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
						startActivity(menuIntent);
						return true;
				}
				return false;
			}
		});
		popup.show();//showing popup menu
	}
}
