package com.climbtheworld.app.intercom.networking.lan.wifidirect;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.intercom.networking.lan.backend.tcp.LanTCPEngine;

@SuppressLint("MissingPermission") //permission is check at activity level.
public class WiFiDirectNetworkManager extends NetworkManager {
	private final WifiP2pManager.Channel p2pChannel;
	private final LanTCPEngine lanEngine;
	WifiP2pManager manager;
	private WifiManager.WifiLock wifiLock;

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("====== REC", action);

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

			} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

				if (manager == null) {
					return;
				}

				NetworkInfo networkInfo = intent
						.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

				if (networkInfo.isConnected()) {

					// We are connected with the other device, request connection
					// info to find group owner IP

					manager.requestConnectionInfo(p2pChannel, new WifiP2pManager.ConnectionInfoListener() {
						@Override
						public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
							Log.d("====== CINF", String.valueOf(wifiP2pInfo));
							if (wifiP2pInfo.groupFormed) {
								openNetwork(wifiP2pInfo);
							} else {
								closeNetwork();
							}
						}
					});
				}

			} else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
				Log.d("====== DCHG", String.valueOf(intent.getParcelableExtra(
						WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)));

			}

		}
	};

	public WiFiDirectNetworkManager(Context parent, IClientEventListener uiHandler, String channel) {
		super(parent, uiHandler, channel);

		lanEngine = new LanTCPEngine(channel, clientHandler, IClientEventListener.ClientType.WIFI_DIRECT);

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
		WifiManager wifiManager = (WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wifiManager == null) {
			return false;
		}

		return wifiManager.isP2pSupported();
	}

	private void openNetwork(WifiP2pInfo wifiP2pInfo) {
		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if(wifiManager != null){
			wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "wifiDirectLock");
			wifiLock.acquire();
		}

		if (wifiP2pInfo.isGroupOwner) {
			lanEngine.openNetwork("", NetworkManager.CTW_UDP_PORT);
		} else {
			lanEngine.openNetwork(wifiP2pInfo.groupOwnerAddress.getHostAddress(), NetworkManager.CTW_UDP_PORT);
		}
	}

	private void closeNetwork() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
		lanEngine.closeNetwork();
	}

	@SuppressLint("MissingPermission") //checked in owning activity
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
		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void sendData(DataFrame data) {
		lanEngine.sendData(data);
	}
}
