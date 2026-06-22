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
import android.widget.Toast;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.transport.ConnectionState;
import com.climbtheworld.app.walkietalkie.transport.DataFrame;
import com.climbtheworld.app.walkietalkie.transport.TransportUtilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WifiAwareTransport implements ITransportLayer {
	private static final String TAG = WifiAwareTransport.class.getSimpleName();
	private static final UUID sessionUUID = UUID.randomUUID();
	private final Context context;
	private final Configs configs;
	private final String instanceUUID;
	private final Map<String, WifiDirectNode> subscribers = new HashMap<>();
	private String channel;
	private String serviceName = null;
	private WifiAwareManager wifiAwareManager;
	private WifiAwareSession awareSession;
	private DiscoverySession serverSession;
	private DiscoverySession clientSession;

	public WifiAwareTransport(Context context, Configs configs) {
		this.context = context;
		this.configs = configs;

		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
		this.instanceUUID = Configs.instance(context).getString(Configs.ConfigKey.instanceUUID);

		initWifiAware();
	}

	private void initWifiAware() {
		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
			Log.e(TAG, "Wi-Fi Aware is not supported on this device.");
			Toast.makeText(context, "Wi-Fi Aware is not supported on this device.",
					Toast.LENGTH_SHORT).show();
			return;
		}

		serviceName =
				TransportUtilities.computeDigest("ctw.walkietalkie." + channel).substring(0, 8)
						.toUpperCase();

		wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);

		if (wifiAwareManager != null && wifiAwareManager.isAvailable()) {
			startAwareSession();
		} else {
			Log.e(TAG, "Wi-Fi Aware is supported but currently unavailable.");
			Toast.makeText(context, "Wi-Fi Aware state lost", Toast.LENGTH_SHORT).show();
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
				.setServiceSpecificInfo(sessionUUID.toString().getBytes())
				.setRangingEnabled(true)
				.build();

		awareSession.publish(config, new DiscoverySessionCallback() {
			@Override
			public void onPublishStarted(PublishDiscoverySession session) {
				super.onPublishStarted(session);
				serverSession = session;
				Log.d(TAG, "Publish session started. Waiting for subscribers...");
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);

				DataFrame dataFrame = DataFrame.parseData(message);

				switch (dataFrame.getFrameType()) {
					case DATA:
						// client data to play
						break;
					case SIGNAL:
						//ping call-sign
						break;
					case NETWORK:

				}

				String msgString = new String(message);
				Log.d(TAG, "Received message from subscriber: " + msgString);

				serverSession.sendMessage(peerHandle, 0, "Hello Client!".getBytes());
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
				clientSession = session;
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

				startRanging(peerHandle);

				Log.d(TAG, "Publisher service discovered!");

				clientSession.sendMessage(peerHandle, 0,
						DataFrame.buildFrame(
								(ConnectionState.AUTH.command + instanceUUID).getBytes(),
								DataFrame.FrameType.NETWORK).toByteArray());
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
				(WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

		if (rttManager != null && rttManager.isAvailable()) {
			RangingRequest rangingRequest = new RangingRequest.Builder()
					.addWifiAwarePeer(peerHandle)
					.build();

			rttManager.startRanging(rangingRequest, context.getMainExecutor(),
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
									Log.i("RTT", "Physical distance to peer: " + distanceMeters +
											" meters.");
								}
							}
						}
					});
		}
	}

	@Override
	public void sendData(byte[] data) {
		for (WifiDirectNode node : subscribers.values()) {
			if (node.state == ConnectionState.ACTIVE) {
				clientSession.sendMessage(node.subscriberPeerHandle, 0,
						DataFrame.buildFrame(data, DataFrame.FrameType.DATA).toByteArray());
			}
		}
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
		if (serverSession != null) {
			serverSession.close();
		}
		if (clientSession != null) {
			clientSession.close();
		}
		if (awareSession != null) {
			awareSession.close();
		}
	}

	private static class WifiDirectNode {
		String uuid;
		int ping = 0;
		ConnectionState state = ConnectionState.AUTH;
		PeerHandle publisherPeerHandle;
		PeerHandle subscriberPeerHandle;
	}
}
