package com.climbtheworld.app.intercon.networking;

public interface INetworkEventListener {
    void onDataReceived(String sourceAddress, byte[] data);
}
