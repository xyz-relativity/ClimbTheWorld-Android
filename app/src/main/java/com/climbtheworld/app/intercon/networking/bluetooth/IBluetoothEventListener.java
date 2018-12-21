package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface IBluetoothEventListener {
    void onDeviceDiscovered(BluetoothDevice device);
    void onDeviceConnected(BluetoothDevice device);
}
