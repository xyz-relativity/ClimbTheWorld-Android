package com.climbtheworld.app.walkietalkie.transport;

import com.climbtheworld.app.walkietalkie.ClientType;

public interface IClientEventListener {

	void onData(String sourceAddress, byte[] data);

	void onControlMessage(String sourceAddress, String message);

	void onClientConnected(ClientType client, String clientId);

	void onClientDisconnected(ClientType client, String clientId);
}
