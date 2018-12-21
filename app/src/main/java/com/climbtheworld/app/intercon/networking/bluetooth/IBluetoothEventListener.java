package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface IBluetoothEventListener {
    void onDeviceDisconnected(BluetoothDevice device);
    void onDeviceConnected(BluetoothDevice device);
    void onDataReceived(String sourceAddress, byte[] data);
}
