package com.climbtheworld.app.intercom.networking;

public interface INetworkBackend {

	void setState(boolean state);

	void onStart();

	void onResume();

	void onPause();

	void onStop();

	void sendData(DataFrame data);
}
