package com.climbtheworld.app.intercom;

public interface IUiEventListener {
	enum ClientType {
		LAN,
		BLUETOOTH,
		P2P_WIFI
	}

	void onData(byte[] data);

	void onClientConnected(ClientType type, String address, String data);

	void onClientUpdated(ClientType type, String address, String data);

	void onClientDisconnected(ClientType type, String address, String data);
}
