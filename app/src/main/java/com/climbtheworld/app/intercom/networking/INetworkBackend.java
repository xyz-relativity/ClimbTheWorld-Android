package com.climbtheworld.app.intercom.networking;

public interface INetworkBackend {

    void onStart();
    void onResume();
    void onDestroy();
    void onPause();
    void addListener(IUiEventListener listener);
    void updateCallSign(String callSign);
}
