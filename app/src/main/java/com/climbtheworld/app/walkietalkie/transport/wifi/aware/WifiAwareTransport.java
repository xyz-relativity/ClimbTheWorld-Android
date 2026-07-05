package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;

import java.util.UUID;

public class WifiAwareTransport implements ITransportLayer {
	private static final String TAG = WifiAwareTransport.class.getSimpleName();
	private final Context context;
	private final Configs configs;
	private final UUID instanceUUID;
	private final ITransportEvents transportEventsListener;
	private final String serviceName = null;
	private String callsign;
	private String channel;
	private WifiAwareManager wifiAwareManager;
	private WifiAwareSession awareSession;
	private HandlerThread awareThread;
	private Handler backgroundHandler;
	private PubSubManager pubSubManager;

	public WifiAwareTransport(Context context, Configs configs,
	                          ITransportEvents transportEventsListener) {
		this.context = context;
		this.configs = configs;

		this.transportEventsListener = transportEventsListener;

		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
		this.callsign = configs.getString(Configs.ConfigKey.intercomCallsign);
		this.instanceUUID = UUID.fromString(
				Configs.instance(context).getString(Configs.ConfigKey.instanceUUID));

		initWifiAware();
	}

	private void initWifiAware() {
		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
			Log.e(TAG, "Wi-Fi Aware is not supported on this device.");
			Toast.makeText(context, "Wi-Fi Aware is not supported on this device.",
					Toast.LENGTH_SHORT).show();
			return;
		}

		wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);

		if (wifiAwareManager != null && wifiAwareManager.isAvailable()) {
			startAwareSession();
		} else {
			Log.e(TAG, "Wi-Fi Aware is supported but currently unavailable.");
			DialogBuilder.toastOnMainThread(context,
					"Wi-Fi Aware is supported but currently unavailable.");
		}
	}

	private void startAwareSession() {
		awareThread = new HandlerThread("WiFiAwareSingleWorker");
		awareThread.start();
		backgroundHandler = new Handler(awareThread.getLooper());

		wifiAwareManager.attach(new AttachCallback() {
			@Override
			public void onAttached(WifiAwareSession session) {
				super.onAttached(session);
				awareSession = session;
				Log.d(TAG, "Successfully attached to Wi-Fi Aware session.");

				pubSubManager =
						new PubSubManager(backgroundHandler, context, channel,
								awareSession,
								transportEventsListener,
								WifiAwareTransport.this).withCallsign(callsign);
				pubSubManager.startPubSub();
			}

			@Override
			public void onAttachFailed() {
				super.onAttachFailed();
				Log.e(TAG, "Failed to attach to Wi-Fi Aware service.");
				DialogBuilder.toastOnMainThread(context,
						"Failed to attach to Wi-Fi Aware service.");
			}
		}, backgroundHandler);
	}

	@Override
	public void sendData(byte[] data) {
		pubSubManager.sendData(data);
	}

	@Override
	public ClientType getType() {
		return ClientType.WIFI_AWARE;
	}

	@Override
	public void notifyConfigChange() {
		if (!channel.equals(configs.getString(Configs.ConfigKey.intercomChannel))) {
			channel = configs.getString(Configs.ConfigKey.intercomChannel);
			onDestroy();
			initWifiAware();
		}

		if (!callsign.equals(configs.getString(Configs.ConfigKey.intercomCallsign))) {
			callsign = configs.getString(Configs.ConfigKey.intercomCallsign);
			pubSubManager.setCallsign(callsign);
		}
	}

	@Override
	public void onDestroy() {
		if (pubSubManager != null) {
			pubSubManager.onDestroy();
		}

		if (awareSession != null) {
			awareSession.close();
		}

		if (awareThread != null) {
			awareThread.quitSafely();
		}
	}
}
