package com.climbtheworld.app.walkietalkie.backend.networking.bluetooth;

import android.bluetooth.BluetoothSocket;

public interface IBluetoothEventListener {
	void onDeviceDisconnected(BluetoothClient device);

	void onDeviceConnected(BluetoothSocket device);

	void onDataReceived(BluetoothClient device, byte[] data);
}
