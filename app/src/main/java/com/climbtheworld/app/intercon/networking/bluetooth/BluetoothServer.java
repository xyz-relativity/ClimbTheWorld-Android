package com.climbtheworld.app.intercon.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import com.climbtheworld.app.intercon.audiotools.IRecordingListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BluetoothServer {
    private BluetoothAdapter mBluetoothAdapter;
    private List<IBluetoothEventListener> listeners = new ArrayList<>();
    private final Handler handler = new Handler();
    private final UUID myUUID;
    private final UUID connectionUUID = UUID.fromString("5995522c-8eb7-47bf-ad12-40a1cd7c426f");
    private AcceptThread serverThread;
    private Map<String, ConnectedThread> activeConnection = new HashMap<>();

    public BluetoothServer(UUID myUUID) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.myUUID = myUUID;
    }

    public void startServer() {
        startBluetoothServer();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initBluetoothDevices();
            }
        }, 250);
    }

    public void stopServer() {
        serverThread.cancel();
    }

    private void initBluetoothDevices() {
        (new Thread() {
            public void run() {
                if (mBluetoothAdapter != null) {
                    mBluetoothAdapter.cancelDiscovery();

                    for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
//                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE)
                        {
                            if (!activeConnection.containsKey(device.getAddress())) {
                                BluetoothSocket socket = null;
                                try {
                                    socket = device.createInsecureRfcommSocketToServiceRecord(connectionUUID);
                                    socket.connect();

                                    for (IBluetoothEventListener listener : listeners) {
                                        listener.onDeviceConnected(device);
                                    }
                                    activeConnection.put(device.getAddress(), new ConnectedThread(socket));

                                } catch (IOException e) {
                                    System.out.println("Fail to connect: " + device.getName() + " " + device.getAddress());
                                }
                            }
                        }
                    }
                }
            }
        }).start();
    }

    private void startBluetoothServer() {
        serverThread = new AcceptThread();
        serverThread.start();
    }

    public void addListener(IBluetoothEventListener bluetoothManager) {
        listeners.add(bluetoothManager);
    }


    public void sendData(byte[] frame, int numberOfReadBytes) {
        for (ConnectedThread socket: activeConnection.values()) {
            socket.write(frame, numberOfReadBytes);
        }
    }

    private void connectionLost(BluetoothSocket socket) {
        activeConnection.remove(socket.getRemoteDevice().getAddress());

        for (IBluetoothEventListener listener : listeners) {
            listener.onDeviceDisconnected(socket.getRemoteDevice());
        }
    }

    private class AcceptThread extends Thread {
        boolean isRunning = false;
        BluetoothServerSocket socket = null;
        public void run() {
            try {
                if (mBluetoothAdapter != null) {
                    socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ClimbTheWorld", connectionUUID);
                    isRunning = true;
                    while (isRunning) {
                        BluetoothSocket connectedClient = socket.accept();
                        if (!activeConnection.containsKey(connectedClient.getRemoteDevice().getAddress())) {
                            for (IBluetoothEventListener listener : listeners) {
                                listener.onDeviceConnected(connectedClient.getRemoteDevice());
                            }

                            activeConnection.put(connectedClient.getRemoteDevice().getAddress(), new ConnectedThread(connectedClient));
                        }
                    }
                }
            } catch (IOException ignore) {
            }
        }

        public void cancel() {
            isRunning = false;
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[IRecordingListener.AUDIO_BUFFER_SIZE];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    System.out.println("received " + bytes);

                    byte[] result = new byte[bytes];
                    System.arraycopy(buffer, 0, result, 0, bytes);

                    for (IBluetoothEventListener listener : listeners) {
                        listener.onDataReceived(mmSocket.getRemoteDevice().getAddress(), result);
                    }
                } catch (IOException e) {
                    connectionLost(mmSocket);
                    break;
                }
            }
        }

        public void write(byte[] frame, int numberOfReadBytes) {
            try {
                mmOutStream.write(frame, 0, numberOfReadBytes);
            } catch (IOException e) {
                connectionLost(mmSocket);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException ignore) {
            }
        }
    }
}
