package com.climbtheworld.app.walkietalkie.networking.lan.backend.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.networking.NetworkManager;
import com.climbtheworld.app.walkietalkie.networking.lan.LanController;

public class WifiNetworkManager extends NetworkManager {
	public static final int CTW_UDP_PORT = 10183;
	private final LanController lanController;
	private final ConnectivityManager connectivityManager;
	private WifiManager.WifiLock wifiLock = null;
	private final ConnectivityManager.NetworkCallback connectionStatus =
			new ConnectivityManager.NetworkCallback() {
				@Override
				public void onAvailable(@NonNull Network network) {
					super.onAvailable(network);
					openNetwork();
				}

				@Override
				public void onCapabilitiesChanged(@NonNull Network network, @NonNull
				NetworkCapabilities networkCapabilities) {
					super.onCapabilitiesChanged(network, networkCapabilities);
				}

				@Override
				public void onLost(@NonNull Network network) {
					super.onLost(network);
					closeNetwork();
				}
			};

	public WifiNetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);

		connectivityManager =
				(ConnectivityManager) parent.getSystemService(Context.CONNECTIVITY_SERVICE);

		lanController = new LanController(parent, channel, clientHandler,
				IClientEventListener.ClientType.WIFI);
	}

	public void onStart() {
		NetworkRequest.Builder builder = new NetworkRequest.Builder();
		builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
		NetworkRequest networkRequest = builder.build();
		connectivityManager.registerNetworkCallback(networkRequest, connectionStatus);
	}

	@Override
	public void onResume() {

	}

	public void onStop() {
		closeNetwork();

		connectivityManager.unregisterNetworkCallback(connectionStatus);
	}

	@Override
	public void onPause() {

	}

	private void openNetwork() {
		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(
					android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wifiLock");
			wifiLock.acquire();
		}

		lanController.startNetwork(CTW_UDP_PORT);
	}

	private void closeNetwork() {
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}

		lanController.closeNetwork();
	}

	@Override
	public void sendData(byte[] data) {
		lanController.sendDataToChannel(data);
	}

	@Override
	public void sendControlMessage(String message) {
		lanController.sendControlMessage(message);
	}
}
