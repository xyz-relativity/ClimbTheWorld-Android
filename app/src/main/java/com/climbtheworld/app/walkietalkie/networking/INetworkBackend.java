package com.climbtheworld.app.walkietalkie.networking;

public interface INetworkBackend {
	void onStart();

	void onResume();

	void onPause();

	void onStop();

	void sendData(DataFrame data);
}
