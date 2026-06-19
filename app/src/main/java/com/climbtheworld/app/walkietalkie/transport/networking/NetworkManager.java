package com.climbtheworld.app.walkietalkie.transport.networking;

import android.content.Context;

import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.transport.IClientEventListener;
import com.climbtheworld.app.walkietalkie.transport.networking.bluetooth.BluetoothNetworkManager;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.wifi.WifiNetworkManager;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.wifiaware.WifiAwareNetworkManager;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.wifidirect.WiFiDirectNetworkManager;

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
