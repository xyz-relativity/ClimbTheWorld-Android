package com.climbtheworld.app.intercom.networking.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

@SuppressLint("MissingPermission") //permission checked at activity level
public class BluetoothClient extends Thread {
	private static final int SOCKET_BUFFER_SIZE = 1024;
	private final IBluetoothEventListener eventListener;
	BluetoothSocket socket;
	private volatile boolean isRunning = false;

	public BluetoothClient(BluetoothSocket socket, IBluetoothEventListener eventListener) {
		this.socket = socket;
		this.eventListener = eventListener;
	}

	public void run() {
		byte[] buffer = new byte[SOCKET_BUFFER_SIZE];
		int bytes;
		isRunning = true;

		// Keep listening to the InputStream while connected
		while (isRunning && socket.isConnected()) {
			try {
				// Read from the InputStream
				bytes = socket.getInputStream().read(buffer);

				byte[] result = new byte[bytes];
				System.arraycopy(buffer, 0, result, 0, bytes);

				eventListener.onDataReceived(socket, result);
			} catch (IOException e) {
				Log.d("======", "Client read fail.", e);
			}
		}

		eventListener.onDeviceDisconnected(socket);
	}
}
