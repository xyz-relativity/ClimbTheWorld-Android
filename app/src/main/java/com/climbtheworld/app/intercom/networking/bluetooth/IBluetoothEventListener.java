package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface IBluetoothEventListener {
	void onDeviceDisconnected(BluetoothDevice device);

	void onDeviceConnected(BluetoothDevice device);

	void onDataReceived(String sourceAddress, byte[] data);
}
