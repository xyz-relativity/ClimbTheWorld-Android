package com.climbtheworld.app.walkietalkie.transport.networking;

public interface INetworkBackend {
	void onStart();

	void onResume();

	void onPause();

	void onStop();

	void sendData(byte[] data);

	void sendControlMessage(String message);
}
