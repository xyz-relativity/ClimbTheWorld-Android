package com.climbtheworld.app.walkietalkie.backend.networking;

import android.content.Context;

import com.climbtheworld.app.walkietalkie.backend.IClientEventListener;
import com.climbtheworld.app.walkietalkie.backend.networking.bluetooth.BluetoothNetworkManager;
import com.climbtheworld.app.walkietalkie.backend.networking.lan.backend.wifi.WifiNetworkManager;
import com.climbtheworld.app.walkietalkie.backend.networking.lan.backend.wifiaware.WifiAwareNetworkManager;
import com.climbtheworld.app.walkietalkie.backend.networking.lan.backend.wifidirect.WiFiDirectNetworkManager;
import com.climbtheworld.app.walkietalkie.frontend.clients.ClientType;

abstract public class NetworkManager implements INetworkBackend {
	protected IClientEventListener clientHandler;
	protected Context parent;
	protected String channel;

	protected NetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		this.clientHandler = clientHandler;
		this.parent = parent;
		this.channel = channel;
	}

	public static class NetworkManagerFactory {
		public static NetworkManager build(ClientType type, Context parent,
		                                   IClientEventListener clientHandler, String channel)
				throws IllegalAccessException {
			switch (type) {
				case WIFI:
					return new WifiNetworkManager(parent, clientHandler, channel);
				case BLUETOOTH:
					return new BluetoothNetworkManager(parent, clientHandler, channel);
				case WIFI_DIRECT:
					return new WiFiDirectNetworkManager(parent, clientHandler, channel);
				case WIFI_AWARE:
					return new WifiAwareNetworkManager(parent, clientHandler, channel);
			}
			throw new IllegalAccessException("Invalid backed client type requested");
		}
	}
}
