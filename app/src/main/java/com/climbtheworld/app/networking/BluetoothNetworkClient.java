package com.climbtheworld.app.networking;

import android.bluetooth.BluetoothDevice;

public class BluetoothNetworkClient implements INetworkClient {
    private BluetoothDevice device;

    public BluetoothNetworkClient(BluetoothDevice device) {
        this.device = device;
    }

    public BluetoothDevice getDevice() {
        return this.device;
    }
}
