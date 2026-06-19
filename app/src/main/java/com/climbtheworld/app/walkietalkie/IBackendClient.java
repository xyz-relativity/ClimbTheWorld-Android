package com.climbtheworld.app.walkietalkie;

public interface IBackendClient {
	void sendData();

	void disconnect();

	ClientType getType();
}
