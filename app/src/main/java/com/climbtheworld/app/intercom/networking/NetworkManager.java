package com.climbtheworld.app.intercom.networking;

import android.content.Context;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothNetworkManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.WiFiDirectNetworkManager;
import com.climbtheworld.app.intercom.networking.wifi.WifiNetworkManager;
import com.climbtheworld.app.intercom.networking.wifiaware.WifiAwareNetworkManager;

abstract public class NetworkManager implements INetworkBackend{
	protected IClientEventListener clientHandler;
	protected Context parent;
	protected String channel;

	public static class NetworkManagerFactory {
		public static NetworkManager build(IClientEventListener.ClientType type, Context parent, IClientEventListener clientHandler, String channel) throws IllegalAccessException {
			switch (type) {
				case WIFI:
					return new WifiNetworkManager(parent, clientHandler, channel);
				case BLUETOOTH:
					return new BluetoothNetworkManager(parent, clientHandler, channel);
				case P2P_WIFI:
					return new WiFiDirectNetworkManager(parent, clientHandler, channel);
				case WIFI_AWARE:
					return new WifiAwareNetworkManager(parent, clientHandler, channel);
			}
			throw new IllegalAccessException("Invalid backed client type requested");
		}
	}

	public NetworkManager (Context parent, IClientEventListener clientHandler, String channel) {
		this.clientHandler = clientHandler;
		this.parent = parent;
		this.channel = channel;
	}
}
