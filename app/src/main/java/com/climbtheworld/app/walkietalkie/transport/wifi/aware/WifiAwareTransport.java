package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;

public class WifiAwareTransport implements ITransportLayer {
	private static final String TAG = WifiAwareTransport.class.getSimpleName();
	private static final long RECOVERY_DELAY_MS = 2_000;
	private final Context context;
	private final Configs configs;
	private final ITransportEvents transportEventsListener;
	private final Handler lifecycleHandler = new Handler(Looper.getMainLooper());
	private final Runnable restartRunnable = new Runnable() {
		@Override
		public void run() {
			restartScheduled = false;
			if (!destroyed && wifiAwareManager != null && wifiAwareManager.isAvailable()) {
				startAwareSession();
			}
		}
	};
	private final BroadcastReceiver awareStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context receiverContext, Intent intent) {
			handleAvailabilityChanged();
		}
	};
	private String callsign;
	private String channel;
	private WifiAwareManager wifiAwareManager;
	private WifiAwareSession awareSession;
	private HandlerThread awareThread;
	private Handler backgroundHandler;
	private PubSubManager pubSubManager;
	private boolean receiverRegistered;
	private boolean attachInProgress;
	private boolean restartScheduled;
	private boolean destroyed;
	private int sessionGeneration;
	private volatile LayerStatus layerStatus = LayerStatus.GRAY;

	public WifiAwareTransport(Context context, Configs configs,
	                          ITransportEvents transportEventsListener) {
		this.context = context.getApplicationContext();
		this.configs = configs;
		this.transportEventsListener = transportEventsListener;
		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
		this.callsign = configs.getString(Configs.ConfigKey.intercomCallsign);

		initWifiAware();
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	private void initWifiAware() {
		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
			Log.e(TAG, "Wi-Fi Aware is not supported on this device.");
			Toast.makeText(context, "Wi-Fi Aware is not supported on this device.",
					Toast.LENGTH_SHORT).show();
			return;
		}

		wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
		context.registerReceiver(awareStateReceiver,
				new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED));
		receiverRegistered = true;
		handleAvailabilityChanged();
	}

	private void handleAvailabilityChanged() {
		lifecycleHandler.post(() -> {
			if (destroyed || wifiAwareManager == null) {
				return;
			}

			if (!wifiAwareManager.isAvailable()) {
				Log.w(TAG, "Wi-Fi Aware became unavailable; suspending transport.");
				lifecycleHandler.removeCallbacks(restartRunnable);
				restartScheduled = false;
				setLayerStatus(LayerStatus.RED);
				resetAwareResources();
				return;
			}

			if (awareSession == null && !attachInProgress && !restartScheduled) {
				startAwareSession();
			}
		});
	}

	private void startAwareSession() {
		if (destroyed || attachInProgress || awareSession != null || wifiAwareManager == null ||
				!wifiAwareManager.isAvailable()) {
			return;
		}

		awareThread = new HandlerThread("WiFiAwareSingleWorker");
		awareThread.start();
		backgroundHandler = new Handler(awareThread.getLooper());
		attachInProgress = true;
		setLayerStatus(LayerStatus.YELLOW);
		final int generation = ++sessionGeneration;

		wifiAwareManager.attach(new AttachCallback() {
			@Override
			public void onAttached(WifiAwareSession session) {
				super.onAttached(session);
				lifecycleHandler.post(() -> handleAttached(session, generation));
			}

			@Override
			public void onAttachFailed() {
				super.onAttachFailed();
				lifecycleHandler.post(() -> handleAttachFailed(generation));
			}
		}, backgroundHandler);
	}

	private void handleAttached(WifiAwareSession session, int generation) {
		if (destroyed || generation != sessionGeneration) {
			session.close();
			return;
		}

		attachInProgress = false;
		awareSession = session;
		Log.d(TAG, "Successfully attached to Wi-Fi Aware session.");

		pubSubManager = new PubSubManager(backgroundHandler, context, channel, awareSession,
				transportEventsListener, this).withCallsign(callsign);
		pubSubManager.startPubSub();
	}

	private void handleAttachFailed(int generation) {
		if (destroyed || generation != sessionGeneration) {
			return;
		}

		attachInProgress = false;
		Log.e(TAG, "Failed to attach to Wi-Fi Aware service; scheduling recovery.");
		setLayerStatus(LayerStatus.RED);
		DialogBuilder.toastOnMainThread(context, "Failed to attach to Wi-Fi Aware service.");
		scheduleRecoveryOnMain("attach failed");
	}

	void requestRecovery(PubSubManager source, String reason) {
		lifecycleHandler.post(() -> {
			if (source == pubSubManager) {
				scheduleRecoveryOnMain(reason);
			}
		});
	}

	private void scheduleRecovery(String reason) {
		lifecycleHandler.post(() -> scheduleRecoveryOnMain(reason));
	}

	private void scheduleRecoveryOnMain(String reason) {
		if (destroyed || restartScheduled) {
			return;
		}

		Log.w(TAG, "Restarting Wi-Fi Aware transport after: " + reason);
		restartScheduled = true;
		if (layerStatus != LayerStatus.RED) {
			setLayerStatus(LayerStatus.YELLOW);
		}
		resetAwareResources();
		lifecycleHandler.postDelayed(restartRunnable, RECOVERY_DELAY_MS);
	}

	private void resetAwareResources() {
		++sessionGeneration;
		attachInProgress = false;

		PubSubManager manager = pubSubManager;
		pubSubManager = null;
		if (manager != null) {
			manager.onDestroy();
		}

		WifiAwareSession session = awareSession;
		awareSession = null;
		if (session != null) {
			session.close();
		}

		if (backgroundHandler != null) {
			backgroundHandler.removeCallbacksAndMessages(null);
		}
		if (awareThread != null) {
			awareThread.quitSafely();
		}
		awareThread = null;
		backgroundHandler = null;
	}

	@Override
	public void sendData(byte[] data) {
		PubSubManager manager = pubSubManager;
		if (manager != null) {
			manager.sendData(data);
		}
	}

	@Override
	public ClientType getType() {
		return ClientType.WIFI_AWARE;
	}

	@Override
	public LayerStatus getLayerStatus() {
		return layerStatus;
	}

	private void setLayerStatus(LayerStatus status) {
		if (layerStatus == status) {
			return;
		}
		layerStatus = status;
		if (transportEventsListener != null) {
			transportEventsListener.onLayerStatusChanged(this, status);
		}
	}

	void onDataPathStatusChanged(PubSubManager source, boolean hasActiveChannel) {
		lifecycleHandler.post(() -> {
			if (destroyed || source != pubSubManager) {
				return;
			}
			setLayerStatus(hasActiveChannel ? LayerStatus.GREEN : LayerStatus.YELLOW);
		});
	}

	@Override
	public void notifyConfigChange() {
		String configuredChannel = configs.getString(Configs.ConfigKey.intercomChannel);
		if (!channel.equals(configuredChannel)) {
			channel = configuredChannel;
			scheduleRecovery("channel changed");
		}

		String configuredCallsign = configs.getString(Configs.ConfigKey.intercomCallsign);
		if (!callsign.equals(configuredCallsign)) {
			callsign = configuredCallsign;
			PubSubManager manager = pubSubManager;
			if (manager != null) {
				manager.setCallsign(callsign);
			}
		}
	}

	@Override
	public void onDestroy() {
		destroyed = true;
		layerStatus = LayerStatus.GRAY;
		lifecycleHandler.removeCallbacks(restartRunnable);
		restartScheduled = false;
		resetAwareResources();

		if (receiverRegistered) {
			try {
				context.unregisterReceiver(awareStateReceiver);
			} catch (IllegalArgumentException e) {
				Log.w(TAG, "Wi-Fi Aware state receiver was already unregistered.", e);
			}
			receiverRegistered = false;
		}
	}
}
