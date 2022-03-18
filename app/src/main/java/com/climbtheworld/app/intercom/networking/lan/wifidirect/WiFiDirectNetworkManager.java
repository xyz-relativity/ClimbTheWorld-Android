package com.climbtheworld.app.intercom.networking.lan.wifidirect;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.utils.ObservableHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuppressLint("MissingPermission") //permission is check at activity level.
public class WiFiDirectNetworkManager extends NetworkManager {
	private final WifiP2pManager.Channel p2pChannel;
	WifiP2pManager manager;
	private final ObservableHashMap<String, P2pWifiClient> connectedClients = new ObservableHashMap<>();

	private static class P2pWifiClient {
		WifiP2pDevice device;

		public P2pWifiClient(WifiP2pDevice device) {
			this.device = device;
		}
	}

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

				if (manager != null) {
					manager.requestPeers(p2pChannel, new WifiP2pManager.PeerListListener() {
						@Override
						public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
							Collection<WifiP2pDevice> refreshedPeers = wifiP2pDeviceList.getDeviceList();
							for (WifiP2pDevice device: refreshedPeers) {
								if (!connectedClients.containsKey(device.deviceAddress)) {
									connectedClients.put(device.deviceAddress, new P2pWifiClient(device));
								}
							}

							List<String> disconnectedClients = new ArrayList<>();
							for (Map.Entry<String, P2pWifiClient> connectedDevice: connectedClients.entrySet()) {
								if (!refreshedPeers.contains(connectedDevice.getValue().device)) {
									disconnectedClients.add(connectedDevice.getKey());
								}
							}

							for (String client : disconnectedClients) {
								connectedClients.remove(client);
							}
						}
					});
				}


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
								//start network
							} else {
								//stop network
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

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, P2pWifiClient>() {
			@Override
			public void onItemPut(String key, P2pWifiClient value) {
				Log.d("====== JOIN", value.device.deviceName + " " + value.device.deviceAddress);
				WifiP2pConfig config = new WifiP2pConfig();
				config.deviceAddress = value.device.deviceAddress;
				config.wps.setup = WpsInfo.PBC;
			}

			@Override
			public void onItemRemove(String key, P2pWifiClient value) {
				Log.d("====== LEFT", value.device.deviceName);
			}
		});
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
