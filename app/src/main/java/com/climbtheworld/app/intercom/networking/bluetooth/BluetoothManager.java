package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.NetworkConnectionManager;
import com.climbtheworld.app.intercom.networking.INetworkBackend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothManager implements IBluetoothEventListener, INetworkBackend {
	private final List<IClientEventListener> uiHandlers = new ArrayList<>();
	private final BluetoothConnection bluetoothConnection;
	private final Map<String, BluetoothDevice> connectedDevices = new HashMap<>();

	public BluetoothManager() {
		bluetoothConnection = new BluetoothConnection(NetworkConnectionManager.myUUID);
		bluetoothConnection.addListener(this);
	}

	public void addListener(IClientEventListener listener) {
		uiHandlers.add(listener);
	}

	@Override
	public void onDeviceConnected(BluetoothDevice device) {
		if (connectedDevices.containsKey(device.getAddress())) {
			return;
		}
		connectedDevices.put(device.getAddress(), device);
		for (IClientEventListener listener : uiHandlers) {
			listener.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
		}
	}

	@Override
	public void onDataReceived(String sourceAddress, byte[] data) {
		for (IClientEventListener uiHandler : uiHandlers) {
			uiHandler.onData(data);
		}
	}

	@Override
	public void onDeviceDisconnected(BluetoothDevice device) {
		connectedDevices.remove(device.getAddress());
		for (IClientEventListener uiHandler : uiHandlers) {
			uiHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
		}
	}

	public void onStart() {
		bluetoothConnection.startServer();
	}

	@Override
	public void onResume() {

	}

	public void updateCallSign(String callSign) {
	}

	public void onDestroy() {
		bluetoothConnection.stopServer();
	}

	@Override
	public void onPause() {

	}

	public void sendData(byte[] frame, int numberOfReadBytes) {
		bluetoothConnection.sendData(frame, numberOfReadBytes);
	}
}
