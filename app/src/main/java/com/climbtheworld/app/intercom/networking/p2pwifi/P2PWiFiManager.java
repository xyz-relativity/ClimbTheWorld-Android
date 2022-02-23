package com.climbtheworld.app.intercom.networking.p2pwifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

public class P2PWiFiManager extends NetworkManager {
	private final WifiP2pManager.Channel p2pChannel;
	WifiP2pManager manager;

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("======", action);

			if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
				// Determine if Wifi P2P mode is enabled or not, alert
				// the Activity.
				int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
				if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
					onStart();
				} else {
					onStop();
				}
			} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

				if (manager != null) {
					manager.requestPeers(p2pChannel, new WifiP2pManager.PeerListListener() {
						@Override
						public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
							Log.d("======", String.valueOf(wifiP2pDeviceList.getDeviceList()));
						}
					});
				}


			} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

				// Connection state changed! We should probably do something about
				// that.

			} else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
				Log.d("======", String.valueOf(intent.getParcelableExtra(
						WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)));

			}

		}
	};

	public P2PWiFiManager(Context parent, IClientEventListener uiHandler, String channel) {
		super(parent, uiHandler, channel);

		IntentFilter intentFilter = new IntentFilter();
		// Indicates a change in the Wi-Fi P2P status.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		// Indicates a change in the list of available peers.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		// Indicates the state of Wi-Fi P2P connectivity has changed.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		// Indicates this device's details have changed.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
		parent.registerReceiver(connectionStatus, intentFilter);

		manager = (WifiP2pManager) parent.getSystemService(Context.WIFI_P2P_SERVICE);
		p2pChannel = manager.initialize(parent, parent.getMainLooper(), null);

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

	@SuppressLint("MissingPermission") //checked in owning activity
	@Override
	public void onStart() {
		if (!isWifiDirectSupported()) {
			return;
		}

		manager.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {

			@Override
			public void onSuccess() {
				// Code for when the discovery initiation is successful goes here.
				// No services have actually been discovered yet, so this method
				// can often be left blank. Code for peer discovery goes in the
				// onReceive method, detailed below.
			}

			@Override
			public void onFailure(int reasonCode) {
				// Code for when the discovery initiation fails goes here.
				// Alert the user that something went wrong.
			}
		});

	}

	@Override
	public void onResume() {

	}

	@Override
	public void onPause() {

	}

	@Override
	public void onStop() {
		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void sendData(DataFrame data) {

	}
}
