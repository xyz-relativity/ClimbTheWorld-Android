package com.climbtheworld.app.intercom.networking.lan.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.intercom.networking.lan.backend.upd.LanUDPEngine;

public class WifiNetworkManager extends NetworkManager {
	private android.net.wifi.WifiManager.WifiLock wifiLock = null;
	private final LanUDPEngine lanUDPEngine;

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
	private WifiManager.MulticastLock multiCastLock;

	public WifiNetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);

		lanUDPEngine = new LanUDPEngine(channel, clientHandler, IClientEventListener.ClientType.WIFI);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		parent.registerReceiver(connectionStatus, intentFilter);
	}

	public void onStart() {

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
		if(wifiManager != null){
			wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "wifiDirectLock");
			wifiLock.acquire();
			multiCastLock = wifiManager.createMulticastLock("wifiDirectMulticastLock");
			multiCastLock.acquire();
		}

		lanUDPEngine.openNetwork("", NetworkManager.CTW_UDP_PORT);
	}

	private void closeNetwork() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
		if (multiCastLock != null && multiCastLock.isHeld()) {
			multiCastLock.release();
		}

		lanUDPEngine.closeNetwork();
	}

	@Override
	public void sendData(DataFrame data) {
		lanUDPEngine.sendData(data);
	}
}
