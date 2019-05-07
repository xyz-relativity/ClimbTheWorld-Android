package com.climbtheworld.app.intercom.networking;

public interface IUiEventListener {
    enum ClientType {
        LAN,
        BLUETOOTH;
    }
    void onData(byte[] data);
    void onClientConnected(ClientType type, String address, String data);
    void onClientUpdated(ClientType type, String address, String data);
    void onClientDisconnected(ClientType type, String address, String data);
}
