package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.NetworkConnectionAgregator;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.INetworkFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

import java.util.HashMap;
import java.util.Map;

public class BluetoothManager extends NetworkManager implements IBluetoothEventListener {
	private final BluetoothConnection bluetoothConnection;
	private final Map<String, BluetoothDevice> connectedDevices = new HashMap<>();

	public BluetoothManager(AppCompatActivity parent, IClientEventListener uiHandler) {
		super(parent, uiHandler);
		bluetoothConnection = new BluetoothConnection(NetworkConnectionAgregator.myUUID);
		bluetoothConnection.addListener(this);
	}

	@Override
	public void onDeviceConnected(BluetoothDevice device) {
		if (connectedDevices.containsKey(device.getAddress())) {
			return;
		}
		connectedDevices.put(device.getAddress(), device);
		uiHandler.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
	}

	@Override
	public void onDataReceived(String sourceAddress, byte[] data) {
		dataFrame.fromData(data, INetworkFrame.FrameType.DATA);
		uiHandler.onData(dataFrame);
	}

	@Override
	public void onDeviceDisconnected(BluetoothDevice device) {
		connectedDevices.remove(device.getAddress());
		uiHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
	}

	public void onStart() {
		bluetoothConnection.startServer();
	}

	@Override
	public void onResume() {

	}

	public void onDestroy() {
		bluetoothConnection.stopServer();
	}

	@Override
	public void sendData(DataFrame data) {
		sendData(data.getData(), data.getLength());
	}

	@Override
	public void onPause() {

	}

	public void sendData(byte[] frame, int numberOfReadBytes) {
		bluetoothConnection.sendData(frame, numberOfReadBytes);
	}
}
