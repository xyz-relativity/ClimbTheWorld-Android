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

import androidx.annotation.RequiresApi;
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
	// The screen's listener, kept to tell the service which one to let go of, see onDestroy.
	private IUiClientEvent uiEventListener;
	private WalkietalkieHandler activeState;
	private AudioManager audioManager;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset bluetoothHeadset;
	// The headset proxy to close, from when it first connects: unlike bluetoothHeadset, it is
	// kept while Bluetooth is off.
	private BluetoothProfile headsetProxy;
	// Whether initIntercom has set all of this up, with no onDestroy since to release it. All
	// that reads and writes it is on the main thread: the screen and Android's callbacks.
	private boolean initialized;
	// Whether the screen is showing, kept to tell the service once it is bound.
	private boolean started;
	private boolean serviceBound;
	private boolean bluetoothReceiverRegistered;
	private boolean audioRoutingConfigured;
	private boolean previousSpeakerphoneOn;
	private boolean previousBluetoothScoOn;
	private int previousAudioMode;
	private final BluetoothProfile.ServiceListener profileListener =
			new BluetoothProfile.ServiceListener() {
				public void onServiceConnected(int profile, BluetoothProfile proxy) {
					if (profile != BluetoothProfile.HEADSET) {
						return;
					}
					if (!initialized) {
						// The screen went away before the proxy connected, with nothing yet to
						// close: a proxy can only be closed once it is there, so it is now.
						closeHeadsetProxy(proxy);
						return;
					}
					if (headsetProxy != null && headsetProxy != proxy) {
						// Asked for before an onDestroy and connected after the initIntercom
						// that followed, which asked for its own: only one is closed in the
						// end, so only one is kept.
						closeHeadsetProxy(headsetProxy);
					}
					headsetProxy = proxy;
					bluetoothHeadset = (BluetoothHeadset) proxy;
					routeCommunicationAudio();
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

	/**
	 * Sets the walkie-talkie up for the screen: the audio routing, and the service, started and
	 * bound. onDestroy releases it all, after which it can be set up again.
	 * <p>
	 * Asked again before that, it does nothing, and the first listener stays: a second time
	 * round would bind the service again and ask for another headset proxy, while onDestroy
	 * only knows of the last of each. The first binding would then keep the service running
	 * after the screen stopped it, and keep the screen with it.
	 */
	public void initIntercom(IUiClientEvent eventReceiver) {
		if (initialized) {
			Log.w(TAG, "The walkie-talkie is set up already.");
			return;
		}
		Context context = parent.get();
		if (context == null) {
			return;
		}
		initialized = true;
		Context applicationContext = context.getApplicationContext();
		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

		configureCommunicationAudio();
		registerBluetoothRouting(context);

		uiEventListener = eventReceiver;
		intercomServiceIntent = new Intent(context, WalkietalkieBackgroundService.class);
		ContextCompat.startForegroundService(applicationContext, intercomServiceIntent);
		intercomServiceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
				backgroundService =
						((WalkietalkieBackgroundService.LocalBinder) iBinder).getService();
				backgroundService.startIntercom(eventReceiver, configs);
				backgroundService.setUiVisible(eventReceiver, started);
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
		initialized = false;
		WalkietalkieHandler lastState = activeState;
		activeState = null;

		// The service outlives the screen, and Android only tells it of the first unbind of its
		// life: it is told here, each time, to let go of this screen.
		if (backgroundService != null) {
			backgroundService.detachUi(uiEventListener, lastState);
		}

		Context context = parent.get();
		if (context != null && serviceBound && intercomServiceConnection != null) {
			context.getApplicationContext().unbindService(intercomServiceConnection);
			serviceBound = false;
		}
		backgroundService = null;
		// Both reach the activity, which the controller may outlive: a headset proxy that has
		// not connected yet keeps profileListener, and so the controller, until it does.
		intercomServiceConnection = null;
		uiEventListener = null;

		// Finished only once the recorder no longer feeds it, as in setRecordingState.
		if (lastState != null) {
			lastState.finish();
		}

		if (stopService && context != null && intercomServiceIntent != null) {
			context.getApplicationContext().stopService(intercomServiceIntent);
		}
		// Not kept for another onDestroy, which would stop the service of a later screen.
		intercomServiceIntent = null;

		releaseCommunicationAudio();
	}

	public void setRecordingState(WalkietalkieHandler newState) {
		WalkietalkieHandler previousState = activeState;
		activeState = newState;
		if (backgroundService != null) {
			backgroundService.setRecordingState(activeState);
		}

		// Finish only once the recorder feeds the new state, so the old one gets no more frames.
		if (previousState != null) {
			previousState.finish();
		}
	}

	public void onStart() {
		started = true;
		notifyUiVisible();
	}

	public void onStop() {
		started = false;
		notifyUiVisible();
	}

	private void notifyUiVisible() {
		if (backgroundService != null) {
			backgroundService.setUiVisible(uiEventListener, started);
		}
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
			// With the application's context, not the activity's: the proxy keeps its context
			// until it is closed, which may be long after the activity is gone, see
			// profileListener.
			bluetoothAdapter.getProfileProxy(context.getApplicationContext(), profileListener,
					BluetoothProfile.HEADSET);
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

	@RequiresApi(Build.VERSION_CODES.S)
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

	private static void closeHeadsetProxy(BluetoothProfile proxy) {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter != null) {
			adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);
		}
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

		// One that has not connected yet is closed as it does, see profileListener.
		if (headsetProxy != null) {
			closeHeadsetProxy(headsetProxy);
		}
		headsetProxy = null;
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
