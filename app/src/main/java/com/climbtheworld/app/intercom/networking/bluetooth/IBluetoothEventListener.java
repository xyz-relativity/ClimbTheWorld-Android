package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothSocket;

public interface IBluetoothEventListener {
	void onDeviceDisconnected(BluetoothSocket device);

	void onDeviceConnected(BluetoothSocket device);

	void onDataReceived(String sourceAddress, byte[] data);
}
