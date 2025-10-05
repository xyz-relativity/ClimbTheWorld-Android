package com.climbtheworld.app.walkietalkie.networking.lan;

public interface INetworkEventListener {
	void onServerStarted();
	void onServerStopped();
	default void onDataReceived(String sourceAddress, byte[] data) {
		//do nothing
	}
}
