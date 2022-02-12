package com.climbtheworld.app.intercom.networking.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothManager extends NetworkManager {
	public static final UUID bluetoothAppUUID = UUID.fromString("0a3f95fe-c6af-45cb-936f-a944548e2def");

	private final BluetoothAdapter bluetoothAdapter;
	private final List<BluetoothSocket> activeConnections = new ArrayList<>();
	private BluetoothServer bluetoothServer;

	private final IBluetoothEventListener btEventHandler = new IBluetoothEventListener() {
		@Override
		public void onDeviceDisconnected(BluetoothSocket device) {
			activeConnections.remove(device);
			uiHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress());
		}

		@Override
		public void onDeviceConnected(BluetoothSocket device) {
			if (activeConnections.contains(device)) {
				return;
			}

			activeConnections.add(device);
			(new BluetoothClient(device, btEventHandler)).start();
			uiHandler.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress());
		}

		@Override
		public void onDataReceived(BluetoothSocket device, byte[] data) {
			inDataFrame.parseData(data);
			uiHandler.onData(inDataFrame, device.getRemoteDevice().getAddress());
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
						onDestroy();
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
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
			return;
		}

		if (ActivityCompat.checkSelfPermission(parent, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
			return;
		}

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		parent.registerReceiver(connectionStatus, intentFilter);

		connectBondedDevices();

		bluetoothServer = new BluetoothServer(bluetoothAdapter, btEventHandler);
		bluetoothServer.startServer(activeConnections);
	}

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
							return;
						}

						if (activeConnections.contains(socket)) {
							return;
						}

						btEventHandler.onDeviceConnected(socket);
					}
				}.start();
			}
		}
	}

	private boolean isDeviceConnected(BluetoothDevice device) {
		for (BluetoothSocket active : activeConnections) {
			if (active.getRemoteDevice() == device) {
				return true;
			}
		}
		return false;
	}

	public void onResume() {

	}

	public void onDestroy() {
		disconnect();
		if (bluetoothServer != null) {
			bluetoothServer.stopServer();
		}
		parent.unregisterReceiver(connectionStatus);
	}

	private void disconnect() {
		for (BluetoothSocket connection : activeConnections) {
			try {
				if (connection.getInputStream() != null) {
					try {
						connection.getInputStream().close();
					} catch (Exception ignored) {
					}
				}

				if (connection.getOutputStream() != null) {
					try {
						connection.getOutputStream().close();
					} catch (Exception ignored) {
					}
				}

				try {
					connection.close();
				} catch (Exception ignored) {
				}

			} catch (IOException ignored) {
			}
		}
	}

	public void onPause() {

	}

	public void sendData(DataFrame frame) {
		for (BluetoothSocket socket : activeConnections) {
			sendData(frame, socket);
		}
	}

	public void sendData(DataFrame frame, BluetoothSocket socket) {
		try {
			socket.getOutputStream().write(frame.toByteArray());
		} catch (IOException e) {
			Log.d("======", "Failed to send data", e);
		}
	}
}
