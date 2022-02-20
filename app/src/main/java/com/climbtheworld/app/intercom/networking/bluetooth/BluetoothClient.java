package com.climbtheworld.app.intercom.networking.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.climbtheworld.app.intercom.networking.DataFrame;

import java.io.IOException;

@SuppressLint("MissingPermission") //permission checked at activity level
public class BluetoothClient extends Thread {
	private static final int SOCKET_BUFFER_SIZE = 1024;
	private final IBluetoothEventListener eventListener;
	private final BluetoothSocket socket;
	private volatile boolean isRunning = false;

	public BluetoothSocket getSocket() {
		return socket;
	}

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
				isRunning = false;
				Log.d("Bluetooth", "Client read fail.", e);
			}
		}

		eventListener.onDeviceDisconnected(socket);
	}

	public void sendData(DataFrame frame) {
		try {
			socket.getOutputStream().write(frame.toByteArray());
			socket.getOutputStream().flush();
		} catch (IOException e) {
			Log.d("Bluetooth", "Failed to send data", e);
		}
	}

	public void closeConnection() {
		if (socket.isConnected()) {
			try {
				if (socket.getInputStream() != null) {
					try {
						socket.getInputStream().close();
					} catch (Exception ignored) {
					}
				}

				if (socket.getOutputStream() != null) {
					try {
						socket.getOutputStream().close();
					} catch (Exception ignored) {
					}
				}

				try {
					socket.close();
				} catch (Exception ignored) {
				}

			} catch (IOException ignored) {
			}
		}

		isRunning = false;
		this.interrupt();
	}
}
