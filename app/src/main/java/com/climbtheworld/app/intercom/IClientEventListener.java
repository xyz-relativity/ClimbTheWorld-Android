package com.climbtheworld.app.intercom;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.networking.DataFrame;

public interface IClientEventListener {
	enum ClientType {
		LAN(R.drawable.ic_wifi),
		BLUETOOTH(R.drawable.ic_bluetooth),
		P2P_WIFI(R.drawable.ic_wifi_direct),
		GENERIC(R.drawable.ic_person);

		ClientType(int icoRes) {
			this.icoRes = icoRes;
		}

		public int icoRes;
	}

	void onData(DataFrame data);

	void onClientConnected(ClientType type, String address, String data);

	void onClientDisconnected(ClientType type, String address, String data);
}
