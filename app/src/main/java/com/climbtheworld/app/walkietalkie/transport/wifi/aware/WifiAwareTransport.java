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
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.transport.TransportUtilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiAwareTransport implements ITransportLayer {
	private static final String TAG = WifiAwareTransport.class.getSimpleName();
	private final Context parent;
	private final Configs configs;
	private final String instanceUUID;
	private final Map<String, WifiDirectNode> clients = new HashMap<>();
	private String channel;
	private String serviceName = null;
	private WifiAwareManager wifiAwareManager;
	private WifiAwareSession awareSession;
	private DiscoverySession serverDiscoverySession;
	private DiscoverySession clientDiscoverySession;

	public WifiAwareTransport(Context parent, Configs configs) {
		this.parent = parent;
		this.configs = configs;

		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
		this.instanceUUID = Configs.instance(parent).getString(Configs.ConfigKey.instanceUUID);

		initWifiAware();
	}

	private void initWifiAware() {
		if (!parent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
			Log.e(TAG, "Wi-Fi Aware is not supported on this device.");
			return;
		}

		serviceName =
				"ctw.walkietalkie." + TransportUtilities.computeDigest(channel).substring(0, 8)
						.toUpperCase();

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
				.setServiceName(serviceName)
				.setRangingEnabled(true)
				.build();

		awareSession.publish(config, new DiscoverySessionCallback() {
			@Override
			public void onPublishStarted(PublishDiscoverySession session) {
				super.onPublishStarted(session);
				serverDiscoverySession = session;
				Log.d(TAG, "Publish session started. Waiting for subscribers...");
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);
				String msgString = new String(message);
				Log.d(TAG, "Received message from subscriber: " + msgString);

				serverDiscoverySession.sendMessage(peerHandle, 0, "Hello Client!".getBytes());
			}
		}, new Handler(Looper.getMainLooper()));
	}

	// --- SUBSCRIBER ROLE (Client device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	private void startSubscribing() {
		SubscribeConfig config = new SubscribeConfig.Builder()
				.setServiceName(serviceName)
				.build();

		awareSession.subscribe(config, new DiscoverySessionCallback() {
			@Override
			public void onSubscribeStarted(SubscribeDiscoverySession session) {
				super.onSubscribeStarted(session);
				clientDiscoverySession = session;
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

				startRanging(peerHandle);

				Log.d(TAG, "Publisher service discovered!");

				clientDiscoverySession.sendMessage(peerHandle, 0, "Hello Server!".getBytes());
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);
				Log.d(TAG, "Received reply from publisher: " + new String(message));
			}
		}, new Handler(Looper.getMainLooper()));
	}

	@SuppressLint("MissingPermission")
	private void startRanging(PeerHandle peerHandle) {
		WifiRttManager rttManager =
				(WifiRttManager) parent.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

		if (rttManager != null && rttManager.isAvailable()) {
			RangingRequest rangingRequest = new RangingRequest.Builder()
					.addWifiAwarePeer(peerHandle)
					.build();

			rttManager.startRanging(rangingRequest, parent.getMainExecutor(),
					new RangingResultCallback() {
						@Override
						public void onRangingFailure(int code) {
							Log.e("RTT", "Distance measurement failed with error code: " + code);
						}

						@Override
						public void onRangingResults(List<RangingResult> results) {
							for (RangingResult result : results) {
								if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
									int distanceMm = result.getDistanceMm();
									double distanceMeters = distanceMm / 1000.0;
									Log.d("RTT", "Physical distance to peer: " + distanceMeters +
											" meters.");
								}
							}
						}
					});
		}
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
		if (!channel.equals(configs.getString(Configs.ConfigKey.intercomChannel))) {
			channel = configs.getString(Configs.ConfigKey.intercomChannel);
			onDestroy();
			initWifiAware();
		}
	}

	@Override
	public void onDestroy() {
		if (serverDiscoverySession != null) {
			serverDiscoverySession.close();
		}
		if (clientDiscoverySession != null) {
			clientDiscoverySession.close();
		}
		if (awareSession != null) {
			awareSession.close();
		}
	}

	private static class WifiDirectNode {
		String uuid;
		PeerHandle serverPeerHandle;
		PeerHandle clientPeerHandle;
	}
}
