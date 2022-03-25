package com.climbtheworld.app.intercom.networking.lan.wifidirect;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.intercom.networking.lan.backend.LanEngine;

import java.util.HashMap;
import java.util.Map;

@SuppressLint("MissingPermission") //permission is check at activity level.
public class WiFiDirectNetworkManager extends NetworkManager {
	private static final String SERVICE_INSTANCE = "_climbtheworld";
	final Map<String, String> discoveredDevices = new HashMap<>();
	private final IntentFilter intentFilter;
	private final LanEngine lanEngine;
	WifiP2pManager manager;
	private WifiP2pManager.Channel p2pChannel;
	private WifiManager.WifiLock wifiLock;
	private WifiP2pDnsSdServiceRequest serviceRequest;

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
				// Determine if Wifi P2P mode is enabled or not, alert
				// the Activity.
				int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
				if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
				} else {
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
							Log.d("wifi2p2 CINF", String.valueOf(wifiP2pInfo));
							if (wifiP2pInfo.groupFormed) {
								openNetwork(wifiP2pInfo);
							} else {
								closeNetwork();
							}
						}
					});
				}

			} else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
				Log.d("wifi2p2", "Device info changed");
			}

		}
	};

	public WiFiDirectNetworkManager(Context parent, IClientEventListener uiHandler, String channel) {
		super(parent, uiHandler, channel);

		lanEngine = new LanEngine(channel, clientHandler, IClientEventListener.ClientType.WIFI_DIRECT);

		intentFilter = new IntentFilter();
		// Indicates a change in the Wi-Fi P2P status.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		// Indicates a change in the list of available peers.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		// Indicates the state of Wi-Fi P2P connectivity has changed.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		// Indicates this device's details have changed.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) parent.getSystemService(Context.WIFI_P2P_SERVICE);
	}

	public boolean isWifiDirectSupported() {
		WifiManager wifiManager = (WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wifiManager == null) {
			return false;
		}

		return wifiManager.isP2pSupported();
	}

	private void startRegistration() {
		//  Create a string map containing information about your service.
		Map<String, String> record = new HashMap<>();
		record.put("channel", channel);

		// Service information.  Pass it an instance name, service type
		// _protocol._transportlayer , and the map containing
		// information other devices will want once they connect to this one.
		WifiP2pDnsSdServiceInfo serviceInfo =
				WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, "_chat._udp", record);

		// Add the local service, sending the service info, network channel,
		// and listener that will be used to indicate success or failure of
		// the request.
		manager.addLocalService(p2pChannel, serviceInfo, new WifiP2pManager.ActionListener() {
			@Override
			public void onSuccess() {
				// Command successful! Code isn't necessarily needed here,
				// Unless you want to update the UI or add logging statements.
				Log.d("wifi2p2", "Local service registered for discovery");
			}

			@Override
			public void onFailure(int arg0) {
				Log.d("wifi2p2", "Failed to register local service: " + arg0);
			}
		});
	}

	private void discoverService() {
		WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
			@Override
			public void onDnsSdTxtRecordAvailable(
					String fullDomain, Map record, WifiP2pDevice device) {
				Log.d("p2p", "DnsSdTxtRecord available -" + record.toString());
			}
		};

		WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
			@Override
			public void onDnsSdServiceAvailable(String instanceName, String registrationType,
			                                    WifiP2pDevice srcDevice) {

				Log.d("p2p", "New dns service available: " + instanceName);
				if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
					connectP2p(srcDevice);
				}
			}
		};

		manager.setDnsSdResponseListeners(p2pChannel, servListener, txtListener);

		serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
		manager.addServiceRequest(p2pChannel,
				serviceRequest,
				new WifiP2pManager.ActionListener() {
					@Override
					public void onSuccess() {
						Log.d("p2p", "Service request created");
					}

					@Override
					public void onFailure(int code) {
						Log.d("p2p", "Service request failed" + code);
					}
				});

		manager.discoverServices(p2pChannel, new WifiP2pManager.ActionListener() {

			@Override
			public void onSuccess() {
				Log.d("p2p", "Service discovered");
			}

			@Override
			public void onFailure(int code) {
				// Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
				Log.d("p2p", "Service discovery failed" + code);
			}
		});

	}

	private void connectP2p(WifiP2pDevice serviceHolder) {
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = serviceHolder.deviceAddress;
		config.wps.setup = WpsInfo.PBC;

		manager.connect(p2pChannel, config, new WifiP2pManager.ActionListener() {

			@Override
			public void onSuccess() {
				Log.d("p2p", "Successfully connected to: " + serviceHolder.deviceAddress);
			}

			@Override
			public void onFailure(int errorCode) {
				Log.d("p2p", "Failed to connected to: " + serviceHolder.deviceAddress);
			}
		});
	}


	private void openNetwork(WifiP2pInfo wifiP2pInfo) {
		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "wifiDirectLock");
			wifiLock.acquire();
		}

		lanEngine.openNetwork(NetworkManager.CTW_UDP_PORT);
	}

	private void closeNetwork() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		lanEngine.closeNetwork();
	}

	@Override
	public void onStart() {
		parent.registerReceiver(connectionStatus, intentFilter);

		p2pChannel = manager.initialize(parent, parent.getMainLooper(), null);
		startRegistration();
		discoverService();
	}

	@Override
	public void onResume() {

	}

	@Override
	public void onPause() {

	}

	@Override
	public void onStop() {
		if (serviceRequest != null) {
			manager.removeServiceRequest(p2pChannel, serviceRequest,
					new WifiP2pManager.ActionListener() {

						@Override
						public void onSuccess() {
						}

						@Override
						public void onFailure(int arg0) {
						}
					});
		}

		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void sendData(DataFrame data) {
		lanEngine.sendData(data);
	}
}
