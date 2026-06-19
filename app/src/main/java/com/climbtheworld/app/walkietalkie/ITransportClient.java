package com.climbtheworld.app.walkietalkie;

public interface ITransportClient {
	void sendData(byte[] data);

	ClientType getType();
}
