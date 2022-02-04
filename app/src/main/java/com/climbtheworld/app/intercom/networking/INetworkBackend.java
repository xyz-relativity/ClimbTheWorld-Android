package com.climbtheworld.app.intercom.networking;

public interface INetworkBackend {

	void onStart();

	void onResume();

	void onPause();

	void onDestroy();

	void sendData(DataFrame data);
}
