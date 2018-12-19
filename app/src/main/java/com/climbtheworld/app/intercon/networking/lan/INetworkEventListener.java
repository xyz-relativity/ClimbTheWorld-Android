package com.climbtheworld.app.intercon.networking.lan;

public interface INetworkEventListener {
    void onDataReceived(String sourceAddress, byte[] data);
}
