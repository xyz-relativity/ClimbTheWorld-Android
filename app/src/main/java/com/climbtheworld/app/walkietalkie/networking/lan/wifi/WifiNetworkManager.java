package com.climbtheworld.app.walkietalkie.networking.lan.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.NetworkManager;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.LanEngine;

public class WifiNetworkManager extends NetworkManager {
	public static final int CTW_UDP_PORT = 10183;
	private final IntentFilter intentFilter;
	private final LanEngine lanEngine;
	private android.net.wifi.WifiManager.WifiLock wifiLock = null;
	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (isConnected(context)) {
				openNetwork();
			} else {
				closeNetwork();
			}
		}

		private boolean isConnected(Context context) {
			ConnectivityManager cm =
					(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			return activeNetwork != null &&
					activeNetwork.isConnected();
		}
	};

	public WifiNetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);

		intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

		lanEngine = new LanEngine(channel, clientHandler, IClientEventListener.ClientType.WIFI);
	}

	public void onStart() {
		parent.registerReceiver(connectionStatus, intentFilter);
	}

	@Override
	public void onResume() {

	}

	public void onStop() {
		closeNetwork();

		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void onPause() {

	}

	private void openNetwork() {
		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "wifiDirectLock");
			wifiLock.acquire();
		}

		lanEngine.openNetwork(CTW_UDP_PORT);
	}

	private void closeNetwork() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		lanEngine.closeNetwork();
	}

	@Override
	public void sendData(DataFrame data) {
		lanEngine.sendData(data);
	}
}
