package com.climbtheworld.app.intercom.networking;

import com.climbtheworld.app.intercom.IUiEventListener;

public interface INetworkBackend {

	void onStart();

	void onResume();

	void onPause();

	void onDestroy();

	void addListener(IUiEventListener listener);

	void updateCallSign(String callSign);
}
