package com.climbtheworld.app.intercom.networking.wifi;

public interface INetworkEventListener {
	void onDataReceived(String sourceAddress, byte[] data);
}
