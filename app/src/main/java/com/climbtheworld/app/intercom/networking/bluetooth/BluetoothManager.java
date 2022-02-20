package com.climbtheworld.app.intercom.networking.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BluetoothManager extends NetworkManager {
	public static final UUID bluetoothAppUUID = UUID.fromString("0a3f95fe-c6af-45cb-936f-a944548e2def");

	private final BluetoothAdapter bluetoothAdapter;
	private final Map<String, BluetoothClient> activeConnections = new HashMap<>();
	private BluetoothServer bluetoothServer;

	private final IBluetoothEventListener btEventHandler = new IBluetoothEventListener() {
		@Override
		public void onDeviceDisconnected(BluetoothSocket device) {
			BluetoothClient client = activeConnections.get(device.getRemoteDevice().getAddress());
			if (client != null) {
				client.closeConnection();
				activeConnections.remove(device.getRemoteDevice().getAddress());
			}
			clientHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress());
		}

		@Override
		public void onDeviceConnected(BluetoothSocket device) {
			if (activeConnections.containsKey(device.getRemoteDevice().getAddress())) {
				return;
			}

			BluetoothClient client = new BluetoothClient(device, btEventHandler);
			activeConnections.put(device.getRemoteDevice().getAddress(), client);
			client.start();
			clientHandler.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress());
		}

		@Override
		public void onDataReceived(BluetoothSocket device, byte[] data) {
			clientHandler.onData(DataFrame.parseData(data), device.getRemoteDevice().getAddress());
		}
	};

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
					case BluetoothAdapter.STATE_OFF:
						onStop();
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						break;
					case BluetoothAdapter.STATE_ON:
						onStart();
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						break;
				}

			}
		}
	};

	public BluetoothManager(Context parent, IClientEventListener uiHandler) {
		super(parent, uiHandler);

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	public void onStart() {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		parent.registerReceiver(connectionStatus, intentFilter);

		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
				&& ActivityCompat.checkSelfPermission(parent, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(parent, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
		{
			return;
		}

		bluetoothAdapter.cancelDiscovery();

		connectBondedDevices();

		bluetoothServer = new BluetoothServer(bluetoothAdapter, btEventHandler);
		bluetoothServer.startServer();
	}

	@SuppressLint("MissingPermission")
	public void connectBondedDevices() {
		//try to connect to bonded devices
		for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
			if (isDeviceConnected(device)) {
				continue;
			}

			int deviceClass = device.getBluetoothClass().getMajorDeviceClass();
			if (deviceClass == BluetoothClass.Device.Major.PHONE
					|| deviceClass == BluetoothClass.Device.Major.COMPUTER /*tablets identify as computers*/) {

				new Thread() {
					@Override
					public void run() {
						BluetoothSocket socket;
						try {
							socket = device.createInsecureRfcommSocketToServiceRecord(BluetoothManager.bluetoothAppUUID);
							socket.connect();
						} catch (IOException e) {
							Log.d("Bluetooth", "Connection to client failed.", e);
							return;
						}

						if (activeConnections.containsKey(socket.getRemoteDevice().getAddress())) {
							return;
						}

						btEventHandler.onDeviceConnected(socket);
					}
				}.start();
			}
		}
	}

	private boolean isDeviceConnected(BluetoothDevice device) {
		for (BluetoothClient active : activeConnections.values()) {
			if (active.getSocket().getRemoteDevice() == device) {
				return true;
			}
		}
		return false;
	}

	public void onResume() {

	}

	public void onStop() {
		disconnect();
		if (bluetoothServer != null) {
			bluetoothServer.stopServer();
		}
		parent.unregisterReceiver(connectionStatus);
	}

	private void disconnect() {
		for (BluetoothClient connection : activeConnections.values()) {
			connection.closeConnection();
		}
	}

	public void onPause() {

	}

	public void sendData(DataFrame frame) {
		for (BluetoothClient client : activeConnections.values()) {
			client.sendData(frame);
		}
	}
}
