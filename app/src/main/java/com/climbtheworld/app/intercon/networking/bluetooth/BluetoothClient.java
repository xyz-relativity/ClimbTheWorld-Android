package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothClient {
    List<BluetoothSocket> activeOutSockets = new ArrayList<>();
    private List<Object> deviceList;

    private void connectBluetoothClients() {
        activeOutSockets.clear();

//        for (DeviceInfo device: deviceList) {
//            if (device.getClient() != null) {
//                try {
//                    BluetoothSocket btSocket = device.getClient().getDevice().createRfcommSocketToServiceRecord(MY_UUID);
//                    if (btSocket != null) {
//                        activeOutSockets.add(btSocket);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
    }
}
