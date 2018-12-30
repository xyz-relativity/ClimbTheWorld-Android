package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.climbtheworld.app.intercon.networking.IUiEventListener;
import com.climbtheworld.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothManager implements IBluetoothEventListener {
    private List<IUiEventListener> uiHandlers = new ArrayList<>();
    private BluetoothServer bluetoothServer;
    private Map<String, BluetoothDevice> connectedDevices = new HashMap<>();

    public BluetoothManager() {
        bluetoothServer = new BluetoothServer(Constants.myUUID);
        bluetoothServer.addListener(this);
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
        bluetoothServer.startServer();
    }

    public void updateCallsign(String s) {
        bluetoothServer.update();
    }

    public void onDestroy() {
        bluetoothServer.stopServer();
    }

    public void sendData(byte[] frame, int numberOfReadBytes) {
        bluetoothServer.sendData(frame, numberOfReadBytes);
    }
}
