package com.climbtheworld.app.intercom.networking.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;

@SuppressLint("MissingPermission") //permission checked at activity level
public class BluetoothServer {
	private final BluetoothAdapter bluetoothAdapter;
	private final IBluetoothEventListener eventListener;
	private ServerThread server;

	public BluetoothServer (BluetoothAdapter bluetoothAdapter, IBluetoothEventListener eventListener) {
		this.bluetoothAdapter = bluetoothAdapter;
		this.eventListener = eventListener;
	}

	public void startServer() {
		stopServer();
		server = new ServerThread();
		server.start();
	}

	class ServerThread extends Thread {

		private volatile boolean isRunning = true;
		BluetoothServerSocket serverSocket = null;

		@Override
		public void run() {
			if (bluetoothAdapter != null) {
				isRunning = true;
				try {
					serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ClimbTheWorld", BluetoothManager.bluetoothAppUUID);
				} catch (IOException e) {
					Log.d("Bluetooth", "Failed to create socket.", e);
					return;
				}

				while (isRunning && bluetoothAdapter.isEnabled()) {
					try {
						BluetoothSocket connectedClient = serverSocket.accept();
						eventListener.onDeviceConnected(connectedClient);
					} catch (IOException e) {
						Log.d("Bluetooth", "Failed to accept client.", e);
					}
				}
			}
		}

		void stopServer() {
			try {
				serverSocket.close();
			} catch (IOException ignore) {
			}
			isRunning = false;
		}
	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
			server = null;
		}
	}
}
