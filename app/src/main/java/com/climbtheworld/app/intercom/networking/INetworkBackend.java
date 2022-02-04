package com.climbtheworld.app.intercom.networking;

import com.climbtheworld.app.intercom.IClientEventListener;

public interface INetworkBackend {

	void onStart();

	void onResume();

	void onPause();

	void onDestroy();

	void addListener(IClientEventListener listener);

	void updateCallSign(String callSign);
}
