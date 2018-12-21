package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothServer {
    private BluetoothAdapter mBluetoothAdapter;
    private List<IBluetoothEventListener> listeners = new ArrayList<>();
    private final UUID myUUID;
    private final UUID connectionUUID = UUID.fromString("00000000-0000-0000-0000-008000000183");

    public BluetoothServer(UUID myUUID) {
        this.myUUID = myUUID;
    }

    public void startServer() {
        initBluetoothDevices();
    }

    public void stopServer() {
    }

    private void initBluetoothDevices() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        (new Thread() {
            public void run() {
                if (mBluetoothAdapter != null) {
                    startBluetoothServer();
                    for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
//                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE)
                        {
                            BluetoothSocket socket = null;
                            try {
                                socket = device.createInsecureRfcommSocketToServiceRecord(connectionUUID);
                                socket.connect();

                                for (IBluetoothEventListener listener : listeners) {
                                    listener.onDeviceConnected(device);
                                }
                            } catch (IOException ignore) {
                                ignore.printStackTrace();
                            }
                        }
                    }
                }
            }
        }).start();
    }

    private void startBluetoothServer() {
        (new Thread() {
            public void run() {
                try {
                    if (mBluetoothAdapter != null) {
                        BluetoothServerSocket socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ClimbTheWorld", connectionUUID);
                        BluetoothSocket test = socket.accept();
//                        activeInSockets.clear();
//                        activeInSockets.add(socket.accept());
                    }
                } catch (IOException ignore) {
                }
            }
        }).start();
    }

    public void addListener(IBluetoothEventListener bluetoothManager) {
        listeners.add(bluetoothManager);
    }
}
