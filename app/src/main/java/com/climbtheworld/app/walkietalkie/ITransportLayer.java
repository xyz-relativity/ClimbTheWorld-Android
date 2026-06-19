package com.climbtheworld.app.walkietalkie;

public interface ITransportLayer {
	void sendData(byte[] data);

	ClientType getType();

	void notifyConfigChange();

	void onDestroy();
}
