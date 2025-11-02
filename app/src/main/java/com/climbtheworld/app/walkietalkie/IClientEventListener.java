package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.R;

public interface IClientEventListener {
	enum ClientType {
		WIFI(R.drawable.ic_wifi),
		BLUETOOTH(R.drawable.ic_bluetooth),
		WIFI_DIRECT(R.drawable.ic_wifi_direct),
		WIFI_AWARE(R.drawable.ic_wifi_direct),
		GENERIC(R.drawable.ic_person);

		ClientType(int icoRes) {
			this.icoRes = icoRes;
		}

		public final int icoRes;
	}

	void onData(String sourceAddress, byte[] data);

	void onControlMessage(String sourceAddress, String message);

	void onClientConnected(ClientType type, String address);

	void onClientDisconnected(ClientType type, String address);
}
