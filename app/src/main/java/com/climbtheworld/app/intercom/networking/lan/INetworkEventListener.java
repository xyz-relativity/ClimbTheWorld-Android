package com.climbtheworld.app.intercom.networking.lan;

public interface INetworkEventListener {
	default void onDataReceived(String sourceAddress, byte[] data) {
		//do nothing
	}
}
