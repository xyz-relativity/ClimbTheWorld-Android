package com.climbtheworld.app.intercom.networking.wifiaware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

public class WifiAwareNetworkManager extends NetworkManager {

	public WifiAwareNetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);
		Log.d("======", "Wifi Aware: " + parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));

		WifiAwareManager wifiAwareManager =
				(WifiAwareManager)parent.getSystemService(Context.WIFI_AWARE_SERVICE);
		IntentFilter filter =
				new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
		BroadcastReceiver myReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				// discard current sessions
				if (wifiAwareManager.isAvailable()) {
					Log.d("======", "WifiAware on");
				} else {
					Log.d("======", "WifiAware off");
				}
			}
		};
		parent.registerReceiver(myReceiver, filter);
	}

	@Override
	public void onStart() {

	}

	@Override
	public void onResume() {

	}

	@Override
	public void onPause() {

	}

	@Override
	public void onStop() {

	}

	@Override
	public void sendData(DataFrame data) {

	}
}
