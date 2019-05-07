package com.climbtheworld.app.intercom.networking.lan;

public interface INetworkEventListener {
    void onDataReceived(String sourceAddress, byte[] data);
}
