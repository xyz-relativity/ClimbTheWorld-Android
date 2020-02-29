package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.climbtheworld.app.intercom.IUiEventListener;
import com.climbtheworld.app.intercom.networking.INetworkBackend;
import com.climbtheworld.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothManager implements IBluetoothEventListener, INetworkBackend {
    private List<IUiEventListener> uiHandlers = new ArrayList<>();
    private BluetoothConnection bluetoothConnection;
    private Map<String, BluetoothDevice> connectedDevices = new HashMap<>();

    public BluetoothManager() {
        bluetoothConnection = new BluetoothConnection(Constants.myUUID);
        bluetoothConnection.addListener(this);
    }

    public void addListener(IUiEventListener listener) {
        uiHandlers.add(listener);
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        if (connectedDevices.containsKey(device.getAddress())) {
            return;
        }
        connectedDevices.put(device.getAddress(), device);
        for (IUiEventListener listener: uiHandlers) {
            listener.onClientConnected(IUiEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
        }
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        for (IUiEventListener uiHandler: uiHandlers) {
            uiHandler.onData(data);
        }
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        connectedDevices.remove(device.getAddress());
        for (IUiEventListener listener: uiHandlers) {
            listener.onClientDisconnected(IUiEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getName());
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
