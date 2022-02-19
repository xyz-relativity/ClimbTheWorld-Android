package com.climbtheworld.app.intercom.networking;

import android.content.Context;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.P2PWiFiManager;
import com.climbtheworld.app.intercom.networking.wifi.LanManager;

abstract public class NetworkManager implements INetworkBackend{
	protected IClientEventListener clientHandler;
	protected Context parent;

	protected DataFrame inDataFrame = new DataFrame();
	protected DataFrame outDataFrame = new DataFrame();

	public static class NetworkManagerFactory {
		public static NetworkManager build(IClientEventListener.ClientType type, Context parent, IClientEventListener clientHandler) throws IllegalAccessException {
			switch (type) {
				case LAN:
					return new LanManager(parent, clientHandler);
				case BLUETOOTH:
					return new BluetoothManager(parent, clientHandler);
				case P2P_WIFI:
					return new P2PWiFiManager(parent, clientHandler);
			}
			throw new IllegalAccessException("Invalid backed client type requested");
		}
	}

	public NetworkManager (Context parent, IClientEventListener clientHandler) {
		this.clientHandler = clientHandler;
		this.parent = parent;
	}
}
