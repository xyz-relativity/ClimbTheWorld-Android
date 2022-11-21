package com.climbtheworld.app.walkietalkie.networking.lan;

public interface INetworkEventListener {
	default void onDataReceived(String sourceAddress, byte[] data) {
		//do nothing
	}
}
