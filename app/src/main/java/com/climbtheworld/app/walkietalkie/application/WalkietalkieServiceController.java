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
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.application.states.WalkietalkieHandler;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public class WalkietalkieServiceController {
	private static final String TAG = WalkietalkieServiceController.class.getSimpleName();
	private final WeakReference<Context> parent;
	private final Configs configs;
	private ServiceConnection intercomServiceConnection;
	private Intent intercomServiceIntent;
	private WalkietalkieBackgroundService backgroundService;
	private WalkietalkieHandler activeState;
	private AudioManager audioManager;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset bluetoothHeadset;
	private boolean serviceBound;
	private boolean bluetoothReceiverRegistered;
	private boolean audioRoutingConfigured;
	private boolean previousSpeakerphoneOn;
	private boolean previousBluetoothScoOn;
	private int previousAudioMode;
	private final BluetoothProfile.ServiceListener profileListener =
			new BluetoothProfile.ServiceListener() {
				public void onServiceConnected(int profile, BluetoothProfile proxy) {
					if (profile == BluetoothProfile.HEADSET) {
						bluetoothHeadset = (BluetoothHeadset) proxy;
						routeCommunicationAudio();
					}
				}

				public void onServiceDisconnected(int profile) {
					if (profile == BluetoothProfile.HEADSET) {
						bluetoothHeadset = null;
						routeCommunicationAudio();
					}
				}
			};
	private final BroadcastReceiver bluetoothConnectReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			routeCommunicationAudio();
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

		configureCommunicationAudio();
		registerBluetoothRouting(context);

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

		releaseCommunicationAudio();
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

	private void configureCommunicationAudio() {
		if (audioManager == null || audioRoutingConfigured) {
			return;
		}

		previousAudioMode = audioManager.getMode();
		previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();
		previousBluetoothScoOn = audioManager.isBluetoothScoOn();
		audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		audioRoutingConfigured = true;
		routeCommunicationAudio();
	}

	private void registerBluetoothRouting(Context context) {
		IntentFilter bluetoothFilter = new IntentFilter();
		bluetoothFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
		bluetoothFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
		context.registerReceiver(bluetoothConnectReceiver, bluetoothFilter);
		bluetoothReceiverRegistered = true;

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter != null) {
			bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET);
		}
	}

	private void routeCommunicationAudio() {
		if (audioManager == null || !audioRoutingConfigured) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			AudioDeviceInfo device = findPreferredCommunicationDevice();
			if (device == null) {
				Log.w(TAG, "No communication audio device is available.");
				return;
			}
			if (!audioManager.setCommunicationDevice(device)) {
				Log.w(TAG, "Failed to select communication audio device type: "
						+ device.getType());
			}
			return;
		}

		boolean bluetoothConnected = isLegacyBluetoothHeadsetConnected();
		if (bluetoothConnected) {
			audioManager.setSpeakerphoneOn(false);
			audioManager.startBluetoothSco();
			audioManager.setBluetoothScoOn(true);
		} else {
			audioManager.stopBluetoothSco();
			audioManager.setBluetoothScoOn(false);
			audioManager.setSpeakerphoneOn(true);
		}
	}

	private AudioDeviceInfo findPreferredCommunicationDevice() {
		int[] preferredTypes = {
				AudioDeviceInfo.TYPE_BLE_HEADSET,
				AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
				AudioDeviceInfo.TYPE_WIRED_HEADSET,
				AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
				AudioDeviceInfo.TYPE_USB_HEADSET,
				AudioDeviceInfo.TYPE_HEARING_AID,
				AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
		};

		List<AudioDeviceInfo> availableDevices = audioManager.getAvailableCommunicationDevices();
		for (int preferredType : preferredTypes) {
			for (AudioDeviceInfo device : availableDevices) {
				if (device.getType() == preferredType) {
					return device;
				}
			}
		}
		return null;
	}

	private boolean isLegacyBluetoothHeadsetConnected() {
		if (bluetoothHeadset != null) {
			try {
				if (!bluetoothHeadset.getConnectedDevices().isEmpty()) {
					return true;
				}
			} catch (SecurityException e) {
				Log.w(TAG, "Bluetooth headset connection state is unavailable.", e);
			}
		}

		for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
			if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
				return true;
			}
		}
		return false;
	}

	private void releaseCommunicationAudio() {
		Context context = parent.get();
		if (context != null && bluetoothReceiverRegistered) {
			try {
				context.unregisterReceiver(bluetoothConnectReceiver);
			} catch (IllegalArgumentException e) {
				Log.d(TAG, "Bluetooth receiver already unregistered.");
			}
			bluetoothReceiverRegistered = false;
		}

		if (bluetoothAdapter != null && bluetoothHeadset != null) {
			bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
		}
		bluetoothHeadset = null;
		bluetoothAdapter = null;

		if (audioManager == null || !audioRoutingConfigured) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			audioManager.clearCommunicationDevice();
		} else {
			audioManager.stopBluetoothSco();
			audioManager.setBluetoothScoOn(previousBluetoothScoOn);
			audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
		}
		audioManager.setMode(previousAudioMode);
		audioRoutingConfigured = false;
	}
}
