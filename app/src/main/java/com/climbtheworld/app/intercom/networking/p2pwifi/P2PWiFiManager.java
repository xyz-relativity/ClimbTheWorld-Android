package com.climbtheworld.app.intercom.networking.p2pwifi;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pManager;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.intercom.IUiEventListener;
import com.climbtheworld.app.intercom.networking.INetworkBackend;

public class P2PWiFiManager implements INetworkBackend {
	private final IntentFilter intentFilter = new IntentFilter();
	private final WifiP2pManager manager;
	private final WifiP2pManager.Channel channel;
	AppCompatActivity parent;
	private WiFiDirectBroadcastReceiver receiver;

	public P2PWiFiManager(AppCompatActivity parent) {
		this.parent = parent;

		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) parent.getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(parent, parent.getMainLooper(), null);
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, parent);
	}

	@Override
	public void onStart() {
		parent.registerReceiver(receiver, intentFilter);
	}

	@Override
	public void onResume() {

	}

	@Override
	public void onDestroy() {
		parent.unregisterReceiver(receiver);

	}

	@Override
	public void onPause() {

	}

	@Override
	public void addListener(IUiEventListener listener) {

	}

	@Override
	public void updateCallSign(String callSign) {

	}

	public void updateChannel(String channel) {

	}
}
