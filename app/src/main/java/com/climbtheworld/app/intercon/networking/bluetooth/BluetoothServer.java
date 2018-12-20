package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothServer {
    private BluetoothAdapter mBluetoothAdapter;
    private List<IBluetoothEventListener> listeners = new ArrayList<>();
    private final UUID myUUID;

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
        if (mBluetoothAdapter != null) {
            for (BluetoothDevice device: mBluetoothAdapter.getBondedDevices())
            {
//                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE)
                {
                    for (IBluetoothEventListener listener: listeners) {
                        listener.onDeviceDiscovered(device);
                    }
                }
            }
        }
    }

    private void startBluetoothListener() {
//        (new Thread() {
//            public void run() {
//                try {
//                    if (mBluetoothAdapter != null) {
//                        BluetoothServerSocket socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("xyz", MY_UUID);
//                        activeInSockets.clear();
//                        activeInSockets.add(socket.accept());
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();

    }

    public void addListener(IBluetoothEventListener bluetoothManager) {
        listeners.add(bluetoothManager);
    }
}
