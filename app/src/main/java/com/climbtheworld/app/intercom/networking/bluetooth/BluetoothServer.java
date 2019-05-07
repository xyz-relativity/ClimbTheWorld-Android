package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.utils.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

public class BluetoothServer {
    private BluetoothAdapter mBluetoothAdapter;
    private List<IBluetoothEventListener> listeners = new ArrayList<>();
    private final Handler handler = new Handler();
    private final UUID myUUID;
    private final UUID connectionUUID = UUID.fromString("5995522c-8eb7-47bf-ad12-40a1cd7c426f");
    private AcceptThread serverThread;
    private Map<String, ConnectedThread> activeConnection = new ConcurrentHashMap<>();

    public BluetoothServer(UUID myUUID) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.myUUID = myUUID;
    }

    public void startServer() {
        startBluetoothServer();
        scanBluetoothDevices();
    }

    public void stopServer() {
        if (serverThread != null) {
            serverThread.cancel();
        }

        for (final ConnectedThread clientThread: activeConnection.values()) {
            clientThread.cancel();
        }
    }

    private void scanBluetoothDevices() {
        Constants.ASYNC_TASK_EXECUTOR.execute(new Runnable() {
            public void run() {
                if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.cancelDiscovery();

                    for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
                        if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) {
                            if (!activeConnection.containsKey(device.getAddress())) {
                                BluetoothSocket socket = null;
                                try {
                                    socket = device.createInsecureRfcommSocketToServiceRecord(connectionUUID);
                                    socket.connect();

                                    newConnection(socket);
                                } catch (IOException ignore) {
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void startBluetoothServer() {
        stopServer();
        serverThread = new AcceptThread();
        serverThread.start();
    }

    public void addListener(IBluetoothEventListener bluetoothManager) {
        listeners.add(bluetoothManager);
    }


    public void sendData(final byte[] frame, final int numberOfReadBytes) {
        for (final ConnectedThread socket: activeConnection.values()) {
            NETWORK_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    socket.write(frame, numberOfReadBytes);
                }
            });
        }
    }

    private void connectionLost(BluetoothSocket socket) {
        activeConnection.remove(socket.getRemoteDevice().getAddress());

        for (IBluetoothEventListener listener : listeners) {
            listener.onDeviceDisconnected(socket.getRemoteDevice());
        }
    }

    private synchronized void newConnection(BluetoothSocket socket) {
        if (!activeConnection.containsKey(socket.getRemoteDevice().getAddress())) {
            ConnectedThread client = new ConnectedThread(socket);
            activeConnection.put(socket.getRemoteDevice().getAddress(), client);
            client.start();

            for (IBluetoothEventListener listener : listeners) {
                listener.onDeviceConnected(socket.getRemoteDevice());
            }
        }
    }

    private class AcceptThread extends Thread {
        private volatile boolean isRunning = false;
        BluetoothServerSocket socket = null;
        public void run() {
            if (mBluetoothAdapter != null) {
                isRunning = true;

                while (mBluetoothAdapter.isEnabled() && isRunning) {
                    try {
                        socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ClimbTheWorld", connectionUUID);

                        BluetoothSocket connectedClient = socket.accept();
                        newConnection(connectedClient);
                    } catch (IOException ignore) {
                    }
                }
                isRunning = false;
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
        public final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private volatile boolean isRunning = false;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[IRecordingListener.AUDIO_BUFFER_SIZE];
            int bytes;
            isRunning = true;

            // Keep listening to the InputStream while connected
            while (isRunning) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    byte[] result = new byte[bytes];
                    System.arraycopy(buffer, 0, result, 0, bytes);

                    for (IBluetoothEventListener listener : listeners) {
                        listener.onDataReceived(mmSocket.getRemoteDevice().getAddress(), result);
                    }
                } catch (IOException ignore) {
                    cancel();
                }
            }
        }

        public synchronized void write(final byte[] frame, final int numberOfReadBytes) {
            try {
                mmOutStream.write(frame, 0, numberOfReadBytes);
            } catch (IOException e) {
                cancel();
            }
        }

        public void cancel() {
            isRunning = false;

            try {
                if (mmInStream != null) {
                    mmInStream.close();
                }

                if (mmOutStream != null) {
                    mmOutStream.close();
                }

                mmSocket.close();
            } catch (IOException ignore) {
            }
            connectionLost(mmSocket);
        }
    }
}
