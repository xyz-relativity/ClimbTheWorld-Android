package com.climbtheworld.app.walkietalkie;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.states.WalkietalkieHandler;

import java.lang.ref.WeakReference;

public class IntercomServiceController {
	private final WeakReference<Context> parent;
	private final Configs configs;
	private ServiceConnection intercomServiceConnection;
	private IntercomBackgroundService backgroundService = null;
	private WalkietalkieHandler activeState;
	private AudioManager audioManager;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;
	final BluetoothProfile.ServiceListener mProfileListener =
			new BluetoothProfile.ServiceListener() {
				public void onServiceConnected(int profile, BluetoothProfile proxy) {
					Log.d("Audio-Bluetooth", "BT Onservice Connected");
					if (profile == BluetoothProfile.HEADSET) {
						mBluetoothHeadset = (BluetoothHeadset) proxy;
					}
				}

				public void onServiceDisconnected(int profile) {
					if (profile == BluetoothProfile.HEADSET) {
						mBluetoothHeadset = null;
					}
				}
			};
	private final BroadcastReceiver bluetoothConnectReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			startBluetoothSCO();
		}
	};

	public IntercomServiceController(Context parent, Configs configs) {
		this.parent = new WeakReference<>(parent);
		this.configs = configs;
	}

	public void initIntercom(IClientEventListener eventReceiver) {
		audioManager = (AudioManager) parent.get().getSystemService(Context.AUDIO_SERVICE);

		startBluetoothSCO();

		parent.get().registerReceiver(bluetoothConnectReceiver,
				new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));

		Intent intercomServiceIntent = new Intent(parent.get(), IntercomBackgroundService.class);
		intercomServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
				backgroundService = ((IntercomBackgroundService.LocalBinder) iBinder).getService();
				backgroundService.startIntercom(eventReceiver, configs);
				backgroundService.setRecordingState(activeState);
			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {
				backgroundService = null;
			}
		};
		parent.get().getApplicationContext()
				.bindService(intercomServiceIntent, intercomServiceConnection,
						Context.BIND_AUTO_CREATE);
	}

	public void updateConfigs() {
		if (backgroundService != null) {
			backgroundService.updateConfigs();
		}
	}

	public void onDestroy() {
		if (intercomServiceConnection != null) {
			parent.get().getApplicationContext().unbindService(intercomServiceConnection);
		}

		if (activeState != null) {
			activeState.finish();
		}

		stopBluetoothSCO();
	}

	public void setRecordingState(WalkietalkieHandler newState) {
		if (activeState != null) {
			activeState.finish();
		}

		this.activeState = newState;
		if (backgroundService != null) {
			backgroundService.setRecordingState(activeState);
		}
	}

	public void sendData(DataFrame frame) {
		if (backgroundService != null) {
			backgroundService.sendData(frame.getData());
		}
	}

	public void sendControlMessage(String message) {
		if (backgroundService != null) {
			backgroundService.sendControlMessage(message);
		}
	}

	public void onStart() {

	}

	private void startBluetoothSCO() {
		// Start Bluetooth SCO
		audioManager.startBluetoothSco();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetoothAdapter.getProfileProxy(parent.get(), mProfileListener, BluetoothProfile.HEADSET);
	}

	private void stopBluetoothSCO() {
		// Stop Bluetooth SCO
		if (audioManager != null) audioManager.stopBluetoothSco();

		if (bluetoothAdapter != null && mBluetoothHeadset != null)
			bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);

		// Unregister the BroadcastReceiver
		try {
			parent.get().unregisterReceiver(bluetoothConnectReceiver);
		} catch (Exception e) {
			Log.d("walkietalkie", "destroy");
		}
	}
}
