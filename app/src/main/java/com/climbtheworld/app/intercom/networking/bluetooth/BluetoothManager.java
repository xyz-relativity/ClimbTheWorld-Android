package com.climbtheworld.app.intercom.networking.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;

import java.util.HashMap;
import java.util.Map;

import co.lujun.lmbluetoothsdk.BluetoothController;
import co.lujun.lmbluetoothsdk.base.BluetoothListener;
import co.lujun.lmbluetoothsdk.base.State;

public class BluetoothManager extends NetworkManager {
	private final BluetoothController bluetoothController;

	private final Map<String, BluetoothDevice> connectedDevices = new HashMap<>();

	public BluetoothManager(AppCompatActivity parent, IClientEventListener uiHandler) {
		super(parent, uiHandler);
		bluetoothController = BluetoothController.getInstance().build(parent);
		bluetoothController.setBluetoothListener(new BluetoothListener() {
			@Override
			public void onActionStateChanged(int preState, int state) {
				DialogBuilder.toastOnMainThread(parent, (String) parent.getResources().getString(R.string.bluetooth_adapter_state, transBtStateAsString(state)));
			}

			@Override
			public void onActionDiscoveryStateChanged(String discoveryState) {}

			@Override
			public void onActionScanModeChanged(int preScanMode, int scanMode) {}

			@Override
			public void onBluetoothServiceStateChanged(final int state) {
				DialogBuilder.toastOnMainThread(parent, (String) parent.getResources().getString(R.string.bluetooth_connection_state, transConnStateAsString(state)));
				if (state == State.STATE_CONNECTED) {
					onDeviceConnected(bluetoothController.getConnectedDevice());
				}
			}

			@Override
			public void onActionDeviceFound(BluetoothDevice device, short rssi) {}

			@Override
			public void onReadData(final BluetoothDevice device, final byte[] data) {
				dataFrame.parseData(data);
				uiHandler.onData(dataFrame);
			}
		});
	}

	public void onDeviceConnected(BluetoothDevice device) {
		if (connectedDevices.containsKey(device.getAddress())) {
			return;
		}
		connectedDevices.put(device.getAddress(), device);
		uiHandler.onClientConnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getUuids()[0].toString());
	}

	public void onDataReceived(String sourceAddress, byte[] data) {
		dataFrame.parseData(data);
		uiHandler.onData(dataFrame);
	}

	public void onDeviceDisconnected(BluetoothDevice device) {
		connectedDevices.remove(device.getAddress());
		uiHandler.onClientDisconnected(IClientEventListener.ClientType.BLUETOOTH, device.getAddress(), device.getUuids()[0].toString());
	}

	public void onStart() {
		if (!bluetoothController.isAvailable() || !bluetoothController.isEnabled()) {
			DialogBuilder.toastOnMainThread(parent, (String) parent.getText(R.string.bluetooth_not_available));
			return;
		}

		bluetoothController.startAsServer();

		for (BluetoothDevice device: bluetoothController.getBondedDevices()) {
			int deviceClass = device.getBluetoothClass().getMajorDeviceClass();
			if (deviceClass == BluetoothClass.Device.Major.PHONE
					||deviceClass == BluetoothClass.Device.Major.COMPUTER /*tablets identify as computers*/ ) {
				bluetoothController.connect(device.getAddress());
			}
		}
	}

	public void onResume() {

	}

	public void onDestroy() {
		bluetoothController.disconnect();
		bluetoothController.release();
	}

	public void onPause() {

	}

	public void sendData(DataFrame frame) {
		bluetoothController.write(frame.toByteArray());
	}

	public static String transConnStateAsString(int state){
		String result;
		if (state == State.STATE_NONE) {
			result = "NONE";
		} else if (state == State.STATE_LISTEN) {
			result = "LISTEN";
		} else if (state == State.STATE_CONNECTING) {
			result = "CONNECTING";
		} else if (state == State.STATE_CONNECTED) {
			result = "CONNECTED";
		} else if (state == State.STATE_DISCONNECTED){
			result = "DISCONNECTED";
		}else if (state == State.STATE_GOT_CHARACTERISTICS){
			result = "CONNECTED, GOT ALL CHARACTERISTICS";
		}
		else{
			result = "UNKNOWN";
		}
		return result;
	}

	public static String transBtStateAsString(int state){
		String result = "UNKNOWN";
		if (state == BluetoothAdapter.STATE_TURNING_ON) {
			result = "TURNING_ON";
		} else if (state == BluetoothAdapter.STATE_ON) {
			result = "ON";
		} else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
			result = "TURNING_OFF";
		}else if (state == BluetoothAdapter.STATE_OFF) {
			result = "OFF";
		}
		return result;
	}
}
