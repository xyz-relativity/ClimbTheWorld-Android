package com.climbtheworld.app.intercom.networking.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.List;

@SuppressLint("MissingPermission") //permission checked at activity level
public class BluetoothServer {
	private final BluetoothAdapter bluetoothAdapter;
	private final IBluetoothEventListener eventListener;
	private ServerThread server;
	private List<BluetoothSocket> activeConnections;

	public BluetoothServer (BluetoothAdapter bluetoothAdapter, IBluetoothEventListener eventListener) {
		this.bluetoothAdapter = bluetoothAdapter;
		this.eventListener = eventListener;
	}

	public void startServer(List<BluetoothSocket> activeConnections) {
		this.activeConnections = activeConnections;

		Log.d(this.getClass().getName(), "======== Bluetooth server start.", new Exception());

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

				Log.d(this.getName(), "======== Staring bluetooth server.", new Exception());

				while (bluetoothAdapter.isEnabled() && isRunning) {
					try {
						serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ClimbTheWorld", BluetoothManager.bluetoothAppUUID);

						BluetoothSocket connectedClient = serverSocket.accept();
						newConnection(connectedClient);
					} catch (IOException e) {
						Log.d(this.getName(), "======== Server failed on accept.", e);
					}
				}
				Log.d(this.getName(), "======== Bluetooth server stopped.");
			}
		}

		private void newConnection(BluetoothSocket connectedClient) {
			if (activeConnections.contains(connectedClient)) {
				return;
			}

			eventListener.onDeviceConnected(connectedClient);
			(new BluetoothClient(connectedClient, eventListener)).start();
		}

		void stopServer() {
			Log.d(this.getName(), "======== Stopping bluetooth server.", new Exception());
			isRunning = false;
			try {
				serverSocket.close();
			} catch (IOException e) {
				Log.d(this.getName(), "======== Failed to close server socket.", e);
			}
		}
	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
			server = null;
		}
	}
}
