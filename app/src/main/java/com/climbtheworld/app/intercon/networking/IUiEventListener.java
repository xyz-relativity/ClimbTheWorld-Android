package com.climbtheworld.app.intercon.networking;

public interface IUiEventListener {
    void onData(byte[] data);
    void onClientConnected(String address, String uuid, String data);
    void onClientUpdated(String address, String uuid, String data);
    void onClientDisconnected(String address, String uuid, String data);
}
