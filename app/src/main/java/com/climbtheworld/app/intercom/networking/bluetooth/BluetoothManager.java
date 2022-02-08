package com.climbtheworld.app.intercom.networking.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission") //permission checked at activity level
public class BluetoothManager extends NetworkManager {
	public static final UUID bluetoothAppUUID = UUID.fromString("0a3f95fe-c6af-45cb-936f-a944548e2def");

	private final BluetoothAdapter bluetoothAdapter;
	private final List<BluetoothSocket> activeConnections = new ArrayList<>();
	private BluetoothServer bluetoothServer;
	private final IBluetoothEventListener btEventHandler = new IBluetoothEventListener() {
		@Override
		public void onDeviceDisconnected(BluetoothSocket device) {
			Log.d(this.getClass().getName(), "======== Removing active connection: " + device.getRemoteDevice().getName(), new Exception());
			activeConnections.remove(device);
			uiHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress(), bluetoothAppUUID.toString());

			Log.d(this.getClass().getName(), "======== Active clients: " + activeConnections);
		}

		@Override
		public void onDeviceConnected(BluetoothSocket device) {
			Log.d(this.getClass().getName(), "======== Adding active connection: " + device.getRemoteDevice().getName(), new Exception());
			activeConnections.add(device);
			uiHandler.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getRemoteDevice().getAddress(), bluetoothAppUUID.toString());

			Log.d(this.getClass().getName(), "======== Active clients: " + activeConnections);
		}

		@Override
		public void onDataReceived(String sourceAddress, byte[] data) {
			dataFrame.parseData(data);
			uiHandler.onData(dataFrame);
		}
	};

	public BluetoothManager(AppCompatActivity parent, IClientEventListener uiHandler) {
		super(parent, uiHandler);
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	public void onStart() {
		if (!bluetoothAdapter.isEnabled()) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Ask.on(parent)
					.id(500) // in case you are invoking multiple time Ask from same activity or fragment
					.forPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
					.withRationales(parent.getString(R.string.intercom_bluetooth_permission_rational)) //optional
					.go();
		}

		bluetoothAdapter.cancelDiscovery();

		connectBondedDevices();

		bluetoothServer = new BluetoothServer(bluetoothAdapter, btEventHandler);
		bluetoothServer.startServer(activeConnections);
	}

	public void connectBondedDevices() {
		//try to connect to bonded devices
		for (BluetoothDevice device: bluetoothAdapter.getBondedDevices()) {
			if (isDeviceConnected(device)) {
				Log.d(this.getClass().getName(), "======== Device already connected: " + device.getName());
				continue;
			}

			int deviceClass = device.getBluetoothClass().getMajorDeviceClass();
			if (deviceClass == BluetoothClass.Device.Major.PHONE
					||deviceClass == BluetoothClass.Device.Major.COMPUTER /*tablets identify as computers*/ ) {

				new Thread() {
					@Override
					public void run() {
						BluetoothSocket socket;
						try {
							socket = device.createInsecureRfcommSocketToServiceRecord(BluetoothManager.bluetoothAppUUID);
							socket.connect();
						} catch (IOException e) {
							Log.d(this.getClass().getName(), "======== Failed to connect to device: " + device.getName(), e);
							return;
						}

						if (activeConnections.contains(socket)) {
							return;
						}

						btEventHandler.onDeviceConnected(socket);
						(new BluetoothClient(socket, btEventHandler)).start();
					}
				}.start();
			}
		}
	}

	private boolean isDeviceConnected(BluetoothDevice device) {
		for (BluetoothSocket active: activeConnections) {
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
		bluetoothServer.stopServer();
	}

	private void disconnect() {
		for (BluetoothSocket connection: activeConnections) {
			try {
				Log.d(this.getClass().getName(), "======== Disconnecting " + connection.getRemoteDevice().getName());

				if (connection.getInputStream() != null) {
					try {connection.getInputStream().close();} catch (Exception ignored) {}
				}

				if (connection.getOutputStream() != null) {
					try {connection.getOutputStream().close();} catch (Exception ignored) {}
				}

				try {connection.close();} catch (Exception ignored) {}

			} catch (IOException e) {
				Log.d(this.getClass().getName(), "======== failed to disconnect client", e);
			}
		}
	}

	public void onPause() {

	}

	public void sendData(DataFrame frame) {
		for (BluetoothSocket socket: activeConnections) {
			try {
				socket.getOutputStream().write(frame.toByteArray());
			} catch (IOException e) {
				Log.d(this.getClass().getName(), "======== Socket already closed: " + socket.getRemoteDevice().getName(), e);
			}
		}
	}
}
