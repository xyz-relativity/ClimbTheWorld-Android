package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.R;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;

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

		public int icoRes;
	}

	void onData(DataFrame data, String address);

	void onClientConnected(ClientType type, String address);

	void onClientDisconnected(ClientType type, String address);
}
