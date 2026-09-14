package com.climbtheworld.app.walkietalkie.application;

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

import androidx.core.content.ContextCompat;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.application.states.WalkietalkieHandler;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public class WalkietalkieServiceController {
	private final WeakReference<Context> parent;
	private final Configs configs;
	private ServiceConnection intercomServiceConnection;
	private Intent intercomServiceIntent;
	private WalkietalkieBackgroundService backgroundService;
	private WalkietalkieHandler activeState;
	private AudioManager audioManager;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset mBluetoothHeadset;
	private boolean serviceBound;
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

	public WalkietalkieServiceController(Context parent, Configs configs) {
		this.parent = new WeakReference<>(parent);
		this.configs = configs;
	}

	public void initIntercom(IUiClientEvent eventReceiver) {
		Context context = parent.get();
		if (context == null) {
			return;
		}
		Context applicationContext = context.getApplicationContext();
		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

		startBluetoothSCO();
		context.registerReceiver(bluetoothConnectReceiver,
				new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));

		intercomServiceIntent = new Intent(context, WalkietalkieBackgroundService.class);
		ContextCompat.startForegroundService(applicationContext, intercomServiceIntent);
		intercomServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
				backgroundService =
						((WalkietalkieBackgroundService.LocalBinder) iBinder).getService();
				backgroundService.startIntercom(eventReceiver, configs);
				backgroundService.setRecordingState(activeState);
			}

			@Override
			public void onServiceDisconnected(ComponentName componentName) {
				backgroundService = null;
			}
		};
		serviceBound = applicationContext.bindService(intercomServiceIntent,
				intercomServiceConnection, Context.BIND_AUTO_CREATE);
	}

	public void updateConfigs() {
		if (backgroundService != null) {
			backgroundService.updateConfigs();
		}
	}

	public void onDestroy(boolean stopService) {
		Context context = parent.get();
		if (context != null && serviceBound && intercomServiceConnection != null) {
			context.getApplicationContext().unbindService(intercomServiceConnection);
			serviceBound = false;
		}
		backgroundService = null;

		if (activeState != null) {
			activeState.finish();
			activeState = null;
		}

		if (stopService && context != null && intercomServiceIntent != null) {
			context.getApplicationContext().stopService(intercomServiceIntent);
		}

		stopBluetoothSCO();
	}

	public void setRecordingState(WalkietalkieHandler newState) {
		if (activeState != null) {
			activeState.finish();
		}

		activeState = newState;
		if (backgroundService != null) {
			backgroundService.setRecordingState(activeState);
		}
	}

	public void onStart() {

	}

	public List<Client> getUiClientList() {
		if (backgroundService == null) {
			return Collections.emptyList();
		}

		return backgroundService.getUiClientList();
	}

	private void startBluetoothSCO() {
		if (audioManager != null) {
			audioManager.startBluetoothSco();
		}

		Context context = parent.get();
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (context != null && bluetoothAdapter != null) {
			bluetoothAdapter.getProfileProxy(context, mProfileListener, BluetoothProfile.HEADSET);
		}
	}

	private void stopBluetoothSCO() {
		if (audioManager != null) {
			audioManager.stopBluetoothSco();
		}

		if (bluetoothAdapter != null && mBluetoothHeadset != null) {
			bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
		}

		Context context = parent.get();
		if (context != null) {
			try {
				context.unregisterReceiver(bluetoothConnectReceiver);
			} catch (IllegalArgumentException e) {
				Log.d("walkietalkie", "Bluetooth receiver already unregistered.");
			}
		}
	}
}
