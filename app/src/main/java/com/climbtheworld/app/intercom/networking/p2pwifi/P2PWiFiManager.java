package com.climbtheworld.app.intercom.networking.p2pwifi;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

public class P2PWiFiManager extends NetworkManager {
	public P2PWiFiManager(Context parent, IClientEventListener uiHandler, String channel) {
		super(parent, uiHandler, channel);
	}

	public boolean isWifiDirectSupported() {
		PackageManager pm = parent.getPackageManager();
		FeatureInfo[] features = pm.getSystemAvailableFeatures();
		for (FeatureInfo info : features) {
			if (info != null && info.name != null && info.name.equalsIgnoreCase("android.hardware.wifi.direct")) {
				return true;
			}
		}
		return false;
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
