package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class BluetoothServer {
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<DeviceInfo> deviceList;
    private final UUID myUUID;

    class DeviceInfo {
        String name;
        String address;
        BluetoothDevice device;
    }

    public BluetoothServer(UUID myUUID) {
        this.myUUID = myUUID;
        initBluetoothDevices();
    }

    private void initBluetoothDevices() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = new ArrayList<>();
        if (mBluetoothAdapter != null) {
            for (BluetoothDevice device: mBluetoothAdapter.getBondedDevices())
            {
                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) {
                    DeviceInfo newDevice = new DeviceInfo();
                    newDevice.name = device.getName();
                    newDevice.address = device.getAddress();
                    newDevice.device = device;
                    deviceList.add(newDevice);
                }
            }
        }

        // No devices found
        if (deviceList.size() == 0) {
//            deviceList.add(new DeviceInfo(getString(R.string.no_clients_found), "", null));
        }

//        LinearLayout bluetoothListView = findViewById(R.id.bluetoothClients);

//        bluetoothListView.removeAllViews();
//        for (DeviceInfo info: deviceList) {
//            final View newViewElement = inflater.inflate(R.layout.list_item_walkie, bluetoothListView, false);
//            ((TextView)newViewElement.findViewById(R.id.deviceName)).setText(info.getName());
//            ((TextView)newViewElement.findViewById(R.id.deviceAddress)).setText(info.getAddress());
//            bluetoothListView.addView(newViewElement);
//        }
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
}
