package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportLayer;

public class WifiAwareTransport implements ITransportLayer {
	private static final String TAG = WifiAwareTransport.class.getSimpleName();
	private static final String SERVICE_NAME = "com.climbtheworld.walkietalkie:";
	private final Context parent;
	private final Configs configs;
	private WifiAwareManager wifiAwareManager;
	private WifiAwareSession awareSession;
	private DiscoverySession discoverySession;

	public WifiAwareTransport(Context parent, Configs configs) {
		this.parent = parent;
		this.configs = configs;

		initWifiAware();
	}

	private void initWifiAware() {
		if (!parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
			Log.e(TAG, "Wi-Fi Aware is not supported on this device.");
			return;
		}

		wifiAwareManager = (WifiAwareManager) parent.getSystemService(Context.WIFI_AWARE_SERVICE);

		if (wifiAwareManager != null && wifiAwareManager.isAvailable()) {
			startAwareSession();
		} else {
			Log.e(TAG, "Wi-Fi Aware is supported but currently unavailable.");
		}
	}

	private void startAwareSession() {
		wifiAwareManager.attach(new AttachCallback() {
			@Override
			public void onAttached(WifiAwareSession session) {
				super.onAttached(session);
				awareSession = session;
				Log.d(TAG, "Successfully attached to Wi-Fi Aware session.");

				startPublishing();
				startSubscribing();
			}

			@Override
			public void onAttachFailed() {
				super.onAttachFailed();
				Log.e(TAG, "Failed to attach to Wi-Fi Aware service.");
			}
		}, new Handler(Looper.getMainLooper()));
	}

	// --- PUBLISHER ROLE (Host device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	private void startPublishing() {
		PublishConfig config = new PublishConfig.Builder()
				.setServiceName(SERVICE_NAME)
				.build();

		awareSession.publish(config, new DiscoverySessionCallback() {
			@Override
			public void onPublishStarted(PublishDiscoverySession session) {
				super.onPublishStarted(session);
				discoverySession = session;
				Log.d(TAG, "Publish session started. Waiting for subscribers...");
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);
				String msgString = new String(message);
				Log.d(TAG, "Received message from subscriber: " + msgString);

				// Send a reply back to the specific peer
				discoverySession.sendMessage(peerHandle, 0, "Hello Client!".getBytes());
			}
		}, new Handler(Looper.getMainLooper()));
	}

	// --- SUBSCRIBER ROLE (Client device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	private void startSubscribing() {
		SubscribeConfig config = new SubscribeConfig.Builder()
				.setServiceName(SERVICE_NAME)
				.build();

		awareSession.subscribe(config, new DiscoverySessionCallback() {
			@Override
			public void onSubscribeStarted(SubscribeDiscoverySession session) {
				super.onSubscribeStarted(session);
				discoverySession = session;
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);
				Log.d(TAG, "Publisher service discovered!");

				// Send an initial message to the discovered publisher
				discoverySession.sendMessage(peerHandle, 0, "Hello Server!".getBytes());
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);
				Log.d(TAG, "Received reply from publisher: " + new String(message));
			}
		}, new Handler(Looper.getMainLooper()));
	}

	@Override
	public void sendData(byte[] data) {

	}

	@Override
	public ClientType getType() {
		return ClientType.WIFI_AWARE;
	}

	@Override
	public void notifyConfigChange() {

	}

	@Override
	public void onDestroy() {
		if (discoverySession != null) {
			discoverySession.close();
		}
		if (awareSession != null) {
			awareSession.close();
		}
	}
}
