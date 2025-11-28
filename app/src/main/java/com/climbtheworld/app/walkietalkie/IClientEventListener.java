package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.walkietalkie.networking.ClientType;

public interface IClientEventListener {

	void onData(String sourceAddress, byte[] data);

	void onControlMessage(String sourceAddress, String message);

	void onClientConnected(ClientType type, String address);

	void onClientDisconnected(ClientType type, String address);
}
